package io.qwenbridge.commerce;

import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/v2")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CommerceResource {
  private final java.util.concurrent.Semaphore streams = new java.util.concurrent.Semaphore(25);
  @Inject CommerceSearchService service;
  @Inject CommerceSchemaRegistry schemas;
  @Inject CommerceEvidenceStore evidence;
  @Inject CommerceAnalytics analytics;
  @Inject CatalogSynchronization catalog;
  @Inject io.qwenbridge.catalog.CatalogNormalizer normalizer;
  @Inject io.qwenbridge.plugins.PluginRegistry plugins;
  @Inject io.qwenbridge.ai.provider.http.JsonHttpProvider httpProvider;
  @Inject io.qwenbridge.connectors.CommerceBackends backends;
  @Inject io.qwenbridge.platform.Entitlements entitlements;
  @Context ContainerRequestContext context;

  @Inject io.qwenbridge.api.requests.ApiRequests requests;

  /** QB-182: the client's request ID when it sent one (so it can cancel), else a fresh one. */
  private static CommerceContracts.SearchRequest identified(CommerceContracts.SearchRequest r) {
    if (r == null) throw new IllegalArgumentException("Search request body is required");
    String id = r.requestId() == null ? java.util.UUID.randomUUID().toString() : r.requestId();
    if (!io.qwenbridge.core.context.RequestIds.isValid(id))
      throw new IllegalArgumentException(
          "requestId must be 1..128 characters of letters, digits, '.', '_', ':' or '-'");
    return r.requestId() == null ? r.withRequestId(id) : r;
  }

  @POST
  @Path("/search")
  public CommerceContracts.SearchResponse search(CommerceContracts.SearchRequest request) {
    var caller = ApiIdentity.caller(context);
    var effective = identified(request);
    requests.requirePageSize(effective.limit());
    return requests.run(
        context, caller, effective.requestId(), () -> service.search(effective, caller));
  }

  /**
   * One v2 progress event (QB-180). {@code version}, {@code kind}, {@code requestId}, {@code
   * response} and {@code message} are the original fields; {@code sequence} (1, 2, … per stream),
   * {@code terminal}, {@code error} (failed events only) and {@code traceId} were added in 1.0.
   */
  public record SearchEvent(
      int version,
      String kind,
      String requestId,
      CommerceContracts.SearchResponse response,
      String message,
      long sequence,
      boolean terminal,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          io.qwenbridge.api.requests.ApiRequests.StreamError error,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String traceId) {}

  @POST
  @Path("/search/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(
      CommerceContracts.SearchRequest request,
      @Context jakarta.ws.rs.sse.SseEventSink sink,
      @Context jakarta.ws.rs.sse.Sse sse) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    var effective = identified(request);
    requests.requirePageSize(effective.limit());
    if (!streams.tryAcquire()) throw new ClientErrorException("Stream capacity reached", 429);
    String id = effective.requestId();
    try {
      requests.stream(
          context,
          caller,
          id,
          "search",
          "Search could not complete",
          sink,
          sse,
          (kind, sequence, terminal, result, message, error, traceId) ->
              new SearchEvent(
                  2,
                  kind,
                  id,
                  (CommerceContracts.SearchResponse) result,
                  message,
                  sequence,
                  terminal,
                  error,
                  traceId),
          () -> service.search(effective, caller));
    } finally {
      streams.release();
    }
  }

  @POST
  @Path("/search/{previous}/refine")
  public CommerceContracts.SearchResponse refine(
      @PathParam("previous") String previous, CommerceContracts.SearchRequest request) {
    var caller = ApiIdentity.caller(context);
    var effective = identified(request);
    requests.requirePageSize(effective.limit());
    return requests.run(
        context, caller, effective.requestId(), () -> service.refine(previous, effective, caller));
  }

  /**
   * QB-132: {@code queries} keeps the apiVersion 2 rewrite strings; {@code items} is the typed,
   * versioned contract ({@code version}): grounded refinements first, ranked deterministically.
   */
  public record Suggestions(
      int apiVersion,
      String baseRequestId,
      List<String> queries,
      String version,
      List<CommerceSuggestions.SuggestionView> items) {
    public Suggestions {
      queries = List.copyOf(queries);
      items = List.copyOf(items);
    }
  }

  @jakarta.inject.Inject CommerceSuggestions suggestionService;

  @GET
  @Path("/search/{previous}/suggestions")
  public Suggestions suggestions(@PathParam("previous") String previous) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    var stored = analytics.previous(caller, previous);
    var set = suggestionService.suggestions(caller, previous);
    return new Suggestions(
        2, previous, stored.suggestions(), set.version(), CommerceSuggestions.view(set));
  }

  /** QB-133: applies one refinement; every other condition of the search is kept. */
  @POST
  @Path("/search/{previous}/suggestions/{suggestion}/apply")
  public CommerceContracts.SearchResponse applySuggestion(
      @PathParam("previous") String previous,
      @PathParam("suggestion") String suggestion,
      @QueryParam("backend") String backend) {
    return suggestionService.apply(ApiIdentity.caller(context), previous, suggestion, backend);
  }

  /** QB-136: negative feedback as a typed context update and the follow-up search. */
  @POST
  @Path("/search/{previous}/feedback")
  public CommerceSuggestions.FeedbackResult feedback(
      @PathParam("previous") String previous,
      @QueryParam("backend") String backend,
      CommerceSuggestions.FeedbackRequest request) {
    return suggestionService.feedback(ApiIdentity.caller(context), previous, request, backend);
  }

  @GET
  @Path("/search/{previous}/debug")
  public Map<String, Object> debug(@PathParam("previous") String previous) {
    var caller = ApiIdentity.caller(context);
    caller.require("debug:read");
    entitlements.require(caller.tenant(), "playground");
    var stored = analytics.previous(caller, previous);
    // The plan is the exact executed IR. Only filter clauses are exposed; no credentials or
    // vectors.
    return Map.of(
        "response",
        stored,
        "compiledFilters",
        stored.queryIR() == null ? List.of() : CommerceCompiler.filters(stored.queryIR()));
  }

  @GET
  @Path("/backends")
  public List<Map<String, Object>> backends() {
    ApiIdentity.caller(context).require("search");
    return backends.capabilities();
  }

  /** QB-157: the connectors, their lifecycle state, capabilities and circuit. */
  @GET
  @Path("/connectors")
  public List<io.qwenbridge.connectors.BuiltInConnectors.View> connectors() {
    ApiIdentity.caller(context).require("search");
    return backends.connectors();
  }

  @GET
  @Path("/schemas/{id}/{version}")
  public CommerceContracts.Schema schema(
      @PathParam("id") String id, @PathParam("version") int version) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    return schemas.get(caller.tenant(), id, version);
  }

  @POST
  @Path("/schemas")
  public CommerceContracts.Schema register(CommerceContracts.Schema schema) {
    var caller = ApiIdentity.caller(context);
    caller.require("schema:write");
    return schemas.register(caller.tenant(), schema);
  }

  @GET
  @Path("/evidence/{id}")
  public CommerceContracts.Attribution evidence(@PathParam("id") String id) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    var item = evidence.get(caller.tenant(), id);
    if (item == null) throw new NotFoundException("Evidence expired or unavailable");
    if (item.source().startsWith("external:")) caller.require("evidence:read");
    String owner = evidence.owner(caller.tenant(), item.id());
    if (owner != null && !owner.equals(caller.subject())) throw new NotFoundException();
    if (owner == null && !item.requestId().isBlank()) analytics.previous(caller, item.requestId());
    else if (item.parentId() != null) {
      var parent = evidence.get(caller.tenant(), item.parentId());
      if (parent == null) throw new NotFoundException("Evidence parent expired");
      String parentOwner = evidence.owner(caller.tenant(), parent.id());
      if (parentOwner != null && !parentOwner.equals(caller.subject()))
        throw new NotFoundException();
      if (parentOwner == null && !parent.requestId().isBlank())
        analytics.previous(caller, parent.requestId());
    }
    return item;
  }

  public record SourceEvidence(
      String source,
      String text,
      String claim,
      java.time.Instant occurredAt,
      List<io.qwenbridge.analysis.model.Grounded.Evidence> spans,
      String parentId) {
    public SourceEvidence {
      spans = spans == null ? List.of() : List.copyOf(spans);
    }
  }

  @POST
  @Path("/evidence")
  public CommerceContracts.Attribution recordSource(SourceEvidence submission) {
    var caller = ApiIdentity.caller(context);
    caller.require("evidence:write");
    return evidence.recordSource(
        caller.tenant(),
        submission.source(),
        submission.text(),
        submission.claim(),
        submission.occurredAt(),
        submission.spans() == null ? List.of() : submission.spans(),
        submission.parentId());
  }

  @GET
  @Path("/evidence/{id}/source")
  public Map<String, String> sourceText(@PathParam("id") String id) {
    var caller = ApiIdentity.caller(context);
    evidence(id);
    String text = evidence.sourceText(caller.tenant(), id);
    if (text == null) throw new NotFoundException("Source expired or unavailable");
    return Map.of("text", text);
  }

  @GET
  @Path("/analytics")
  public List<CommerceAnalytics.Event> analytics() {
    var caller = ApiIdentity.caller(context);
    caller.require("analytics:read");
    return analytics.recent(caller.tenant());
  }

  public record Click(String requestId, String productId) {}

  public record EvaluationBatch(List<CommerceEvaluation.Judgment> judgments, int k) {
    public EvaluationBatch {
      judgments = List.copyOf(judgments);
    }
  }

  @POST
  @Path("/analytics/evaluate")
  public CommerceEvaluation.RetrievalReport evaluate(EvaluationBatch batch) {
    ApiIdentity.caller(context).require("analytics:read");
    return CommerceEvaluation.retrieval(batch.judgments(), batch.k());
  }

  @POST
  @Path("/analytics/calibration")
  public CommerceEvaluation.CalibrationReport calibration(
      List<CommerceEvaluation.Observation> data) {
    ApiIdentity.caller(context).require("analytics:read");
    return CommerceEvaluation.calibration(data);
  }

  @POST
  @Path("/analytics/click")
  public Map<String, String> click(Click click) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    analytics.click(caller, click.requestId(), click.productId());
    return Map.of("status", "recorded");
  }

  @POST
  @Path("/catalog/normalize")
  public io.qwenbridge.catalog.CatalogProduct normalize(Map<String, Object> document) {
    ApiIdentity.caller(context).require("catalog:write");
    return normalizer.normalize(
        "http-json", java.util.Objects.toString(document.get("id"), "preview"), document);
  }

  /**
   * QB-092 – QB-097: previews the canonical product for a catalog record: every value with its
   * provenance and certainty, the per-currency price ranges and the versioned embedding text.
   * Nothing is stored. {@code connector} selects the pack mapping (default {@code http-json}).
   */
  @POST
  @Path("/catalog/canonical")
  public Map<String, Object> canonical(
      @QueryParam("connector") @DefaultValue("http-json") String connector,
      Map<String, Object> document) {
    ApiIdentity.caller(context).require("catalog:write");
    if (document == null) throw new BadRequestException("Catalog document required");
    if (!connector.matches("[a-z0-9-]{1,64}")) throw new BadRequestException("Invalid connector");
    var product =
        normalizer.canonical(
            connector, java.util.Objects.toString(document.get("id"), "preview"), document);
    return Map.of(
        "product",
        product,
        "priceRanges",
        product.priceRanges(),
        "productText",
        io.qwenbridge.catalog.canonical.ProductTextTemplate.render(product));
  }

  @POST
  @Path("/catalog/sync")
  public Map<String, Object> synchronize() {
    return catalog.synchronize(ApiIdentity.caller(context));
  }

  @GET
  @Path("/plugins")
  public List<io.qwenbridge.plugin.QwenBridgePlugin.Manifest> plugins() {
    var caller = ApiIdentity.caller(context);
    caller.require("plugins:read");
    return plugins.manifests();
  }

  @GET
  @Path("/ai/providers")
  public List<Map<String, Object>> providers() {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    return List.of(
        Map.of(
            "id",
            "ollama",
            "capabilities",
            List.of("chat", "stream", "embedding", "structured-output")),
        Map.of(
            "id",
            "http-json",
            "configured",
            httpProvider.configured(),
            "manifest",
            httpProvider.manifest()));
  }
}
