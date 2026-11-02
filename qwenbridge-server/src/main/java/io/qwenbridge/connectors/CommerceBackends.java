package io.qwenbridge.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.commerce.*;
import io.qwenbridge.commerce.CommerceContracts.Plan;
import io.qwenbridge.execution.provider.model.*;
import io.qwenbridge.platform.Entitlements;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class CommerceBackends {
  public record Page(SearchResultSet results, String nextCursor, String backend) {}

  private final Map<String, CommerceBackend> backends;
  private final String selected;
  private final BuiltInConnectors connectors;
  private final Entitlements entitlements;
  private final ObjectMapper mapper;

  public CommerceBackends(
      OpenSearchBackend os,
      MemoryCommerceBackend memory,
      Entitlements entitlements,
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.commerce.backend", defaultValue = "memory")
          String selected) {
    this(os, memory, entitlements, mapper, selected, null);
  }

  /** QB-158: the guard applied to every built-in connector call. */
  public record Guard(int failureThreshold, Duration openFor, Duration timeout) {}

  @jakarta.inject.Inject
  public CommerceBackends(
      OpenSearchBackend os,
      MemoryCommerceBackend memory,
      Entitlements entitlements,
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.commerce.backend", defaultValue = "memory")
          String selected,
      @ConfigProperty(name = "qwenbridge.connectors.circuit.failure-threshold", defaultValue = "5")
          int failureThreshold,
      @ConfigProperty(name = "qwenbridge.connectors.circuit.open-for", defaultValue = "30s")
          Duration openFor,
      @ConfigProperty(name = "qwenbridge.connectors.call-timeout", defaultValue = "65s")
          Duration timeout) {
    this(os, memory, entitlements, mapper, selected, new Guard(failureThreshold, openFor, timeout));
  }

  public CommerceBackends(
      OpenSearchBackend os,
      MemoryCommerceBackend memory,
      Entitlements entitlements,
      ObjectMapper mapper,
      String selected,
      Guard guard) {
    backends = Map.of(os.id(), os, memory.id(), memory);
    this.selected = selected;
    this.connectors =
        guard == null
            ? null
            : new BuiltInConnectors(
                List.of(os, memory), guard.failureThreshold(), guard.openFor(), guard.timeout());
    this.entitlements = entitlements;
    this.mapper = mapper.copy();
    if (!backends.containsKey(selected))
      throw new IllegalArgumentException("Unknown configured backend");
  }

  /** The built-in connectors' lifecycle, capabilities and circuit state (QB-157). */
  public List<BuiltInConnectors.View> connectors() {
    return connectors == null ? List.of() : connectors.views();
  }

  public String selected() {
    return selected;
  }

  public CommerceBackend backend(String tenant, String requested) {
    String id = requested == null ? selected : requested;
    var backend = backends.get(id);
    if (backend == null) throw new IllegalArgumentException("Unknown backend");
    // OpenSearch and the in-process backend are part of the base search entitlement.
    entitlements.require(
        tenant, id.equals("opensearch") || id.equals(MemoryCommerceBackend.ID) ? "search" : id);
    return backend;
  }

  public List<Map<String, Object>> capabilities() {
    return backends.values().stream()
        .map(
            b ->
                Map.<String, Object>of(
                    "id",
                    b.id(),
                    "configured",
                    b.configured(),
                    "modes",
                    b.modes(),
                    "selected",
                    b.id().equals(selected)))
        .toList();
  }

  public Page search(
      ApiIdentity.Principal caller,
      String requested,
      String text,
      Plan plan,
      String mode,
      List<Double> embedding,
      int limit,
      String cursor) {
    CommerceBackend backend = backend(caller.tenant(), requested);
    // QB-076: a connector executes only through the central policy. The plan's tenant is the
    // resource tenant, so a plan compiled for another tenant can never run with this caller.
    caller.require(
        "search",
        io.qwenbridge.core.policy.Resource.of("index", plan.tenant())
            .inDomain("commerce")
            .via(backend.id()));
    String scope =
        ApiIdentity.digest(
            caller.tenant()
                + "|"
                + caller.subject()
                + "|"
                + backend.id()
                + "|"
                + text
                + "|"
                + mode
                + "|"
                + plan.schemaId()
                + ":"
                + plan.schemaVersion()
                + "|"
                + plan.constraints().stream()
                    .map(c -> c.field() + ":" + c.operator() + ":" + c.value())
                    .sorted()
                    .toList());
    int offset = 0;
    if (cursor != null) {
      try {
        var decoded = mapper.readTree(Base64.getUrlDecoder().decode(cursor));
        if (!scope.equals(decoded.path("scope").asText()))
          throw new IllegalArgumentException("Cursor belongs to another query");
        offset = decoded.path("offset").asInt(-1);
      } catch (java.io.IOException ex) {
        throw new IllegalArgumentException("Invalid cursor", ex);
      }
    }
    var query = new CommerceBackend.Query(text, plan, mode, embedding, limit, offset);
    SearchResultSet raw =
        connectors == null
            ? backend.search(query)
            : connectors.read(backend.id(), () -> backend.search(query));
    List<SearchHit> hits =
        raw.hits().stream()
            .map(
                h ->
                    new SearchHit(
                        Objects.toString(h.document().get("documentId"), h.id()),
                        h.score(),
                        h.document(),
                        h.metadata()))
            .toList();
    for (var hit : hits)
      if (!CommerceCompiler.matches(plan, hit.document()))
        throw new IllegalStateException("Backend violated tenant or query constraints");
    String next = null;
    int after = offset + hits.size();
    if (hits.size() == limit && after < raw.totalHits() && after < 10000)
      try {
        next =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(Map.of("scope", scope, "offset", after)));
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        throw new IllegalStateException(ex);
      }
    return new Page(
        new SearchResultSet(hits, raw.totalHits(), raw.tookMillis()), next, backend.id());
  }

  public void upsert(String tenant, String id, Map<String, Object> document) {
    upsert(tenant, null, id, document);
  }

  public void upsert(String tenant, String requested, String id, Map<String, Object> document) {
    ObjectMapper json =
        mapper
            .copy()
            .enable(
                com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    Map<String, Object> data =
        json.convertValue(
            document, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    var target = backend(tenant, requested);
    if (connectors == null) target.upsert(tenant, id, data);
    else
      connectors.write(
          target.id(),
          io.qwenbridge.core.connector.ConnectorCapabilities.Access.WRITE,
          () -> {
            target.upsert(tenant, id, data);
            return Boolean.TRUE;
          });
  }

  public void delete(String tenant, String id) {
    var target = backend(tenant, null);
    if (connectors == null) target.delete(tenant, id);
    else
      connectors.write(
          target.id(),
          io.qwenbridge.core.connector.ConnectorCapabilities.Access.DELETE,
          () -> {
            target.delete(tenant, id);
            return Boolean.TRUE;
          });
  }
}
