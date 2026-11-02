package io.qwenbridge.sdk.commerce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CommerceContracts {
  private CommerceContracts() {}

  public record Request(
      String requestId,
      String query,
      String catalogLanguage,
      String schemaId,
      Integer schemaVersion,
      String mode,
      boolean rerank,
      String backend,
      Integer limit,
      String cursor,
      List<String> lockedFields,
      @JsonInclude(JsonInclude.Include.NON_NULL) String contract) {

    /** The planned Commerce Search Contract (ADR 0031); the server chooses the strategy. */
    public static final String CONTRACT_V1 = "commerce-search/1.0";

    /** The 0.9 shape (apiVersion 2 behaviour, no contract). */
    public Request(
        String requestId,
        String query,
        String catalogLanguage,
        String schemaId,
        Integer schemaVersion,
        String mode,
        boolean rerank,
        String backend,
        Integer limit,
        String cursor,
        List<String> lockedFields) {
      this(
          requestId,
          query,
          catalogLanguage,
          schemaId,
          schemaVersion,
          mode,
          rerank,
          backend,
          limit,
          cursor,
          lockedFields,
          null);
    }

    /** A {@value #CONTRACT_V1} request: the planner chooses the retrieval strategy. */
    public static Request planned(String query) {
      return new Request(
          null, query, "en", "commerce", 1, null, false, null, 10, null, List.of(), CONTRACT_V1);
    }

    /** This request with {@code id} as its request ID (used to cancel it). */
    public Request withRequestId(String id) {
      return new Request(
          id,
          query,
          catalogLanguage,
          schemaId,
          schemaVersion,
          mode,
          rerank,
          backend,
          limit,
          cursor,
          lockedFields,
          contract);
    }

    public Request(
        String requestId,
        String query,
        String catalogLanguage,
        String schemaId,
        Integer schemaVersion,
        String mode,
        boolean rerank) {
      this(
          requestId,
          query,
          catalogLanguage,
          schemaId,
          schemaVersion,
          mode,
          rerank,
          null,
          10,
          null,
          List.of(),
          null);
    }

    public Request(String query) {
      this(null, query, "en", "commerce", 1, "KEYWORD", false);
    }

    public Request {
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Span(String source, int start, int end, String quote) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Constraint(String field, String operator, String value, Span evidence) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Plan(
      int version,
      String schemaId,
      int schemaVersion,
      String tenant,
      List<Constraint> constraints,
      Map<String, FieldSpec> fields) {

    /** The 0.9 shape. */
    public Plan(
        int version,
        String schemaId,
        int schemaVersion,
        String tenant,
        List<Constraint> constraints) {
      this(version, schemaId, schemaVersion, tenant, constraints, Map.of());
    }

    public Plan {
      constraints = constraints == null ? List.of() : List.copyOf(constraints);
      fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
  }

  /** A schema field the plan was validated against: type, allowed operators, normalization. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FieldSpec(
      String type, List<String> operators, String indexField, String normalization) {
    public FieldSpec {
      operators = operators == null ? List.of() : List.copyOf(operators);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Translation(
      int version,
      String original,
      String sourceLanguage,
      String targetLanguage,
      String translated,
      boolean constraintsPreserved,
      List<String> issues) {
    public Translation {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Hit(
      String id, double score, Map<String, Object> document, Map<String, Object> metadata) {

    /** The 0.9 shape. */
    public Hit(String id, double score, Map<String, Object> document) {
      this(id, score, document, Map.of());
    }

    public Hit {
      document = document == null ? Map.of() : Map.copyOf(document);
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Results(List<Hit> hits, long totalHits, long tookMillis, boolean empty) {

    /** The 0.9 shape. */
    public Results(List<Hit> hits, long totalHits, long tookMillis) {
      this(hits, totalHits, tookMillis, hits == null || hits.isEmpty());
    }

    public Results {
      hits = hits == null ? List.of() : List.copyOf(hits);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Confidence(
      double value, String method, boolean calibrated, Map<String, Double> signals) {
    public Confidence {
      signals = signals == null ? Map.of() : Map.copyOf(signals);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Attribution(
      String id,
      String tenant,
      String requestId,
      String source,
      Instant occurredAt,
      Instant observedAt,
      String claim,
      List<Span> spans,
      String parentId) {
    public Attribution {
      spans = spans == null ? List.of() : List.copyOf(spans);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Response(
      int apiVersion,
      String requestId,
      String status,
      Translation translation,
      String intent,
      Plan queryIR,
      List<String> clarificationQuestions,
      Results results,
      Confidence confidence,
      List<Attribution> evidence,
      List<String> suggestions,
      String reranking,
      long tookMs,
      String nextCursor,
      Map<String, Long> timings,
      List<Policy> policies,
      List<String> lockedFields,
      String contract,
      Retrieval retrieval,
      Interpretation interpretation,
      List<SubQuery> subqueries,
      List<Clarification> clarifications,
      io.qwenbridge.sdk.spelling.Spelling spelling,
      io.qwenbridge.sdk.expansion.Expansion expansion) {

    /** The shape without multilingual expansion (before QB-286). */
    public Response(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        Results results,
        Confidence confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs,
        String nextCursor,
        Map<String, Long> timings,
        List<Policy> policies,
        List<String> lockedFields,
        String contract,
        Retrieval retrieval,
        Interpretation interpretation,
        List<SubQuery> subqueries,
        List<Clarification> clarifications,
        io.qwenbridge.sdk.spelling.Spelling spelling) {
      this(
          apiVersion,
          requestId,
          status,
          translation,
          intent,
          queryIR,
          clarificationQuestions,
          results,
          confidence,
          evidence,
          suggestions,
          reranking,
          tookMs,
          nextCursor,
          timings,
          policies,
          lockedFields,
          contract,
          retrieval,
          interpretation,
          subqueries,
          clarifications,
          spelling,
          null);
    }

    /** The 1.0 shape without typo processing (before QB-291). */
    public Response(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        Results results,
        Confidence confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs,
        String nextCursor,
        Map<String, Long> timings,
        List<Policy> policies,
        List<String> lockedFields,
        String contract,
        Retrieval retrieval,
        Interpretation interpretation,
        List<SubQuery> subqueries,
        List<Clarification> clarifications) {
      this(
          apiVersion,
          requestId,
          status,
          translation,
          intent,
          queryIR,
          clarificationQuestions,
          results,
          confidence,
          evidence,
          suggestions,
          reranking,
          tookMs,
          nextCursor,
          timings,
          policies,
          lockedFields,
          contract,
          retrieval,
          interpretation,
          subqueries,
          clarifications,
          null);
    }

    /** The 0.9 shape. */
    public Response(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        Results results,
        Confidence confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs,
        String nextCursor,
        Map<String, Long> timings,
        List<Policy> policies,
        List<String> lockedFields) {
      this(
          apiVersion,
          requestId,
          status,
          translation,
          intent,
          queryIR,
          clarificationQuestions,
          results,
          confidence,
          evidence,
          suggestions,
          reranking,
          tookMs,
          nextCursor,
          timings,
          policies,
          lockedFields,
          null,
          null,
          null,
          List.of(),
          List.of());
    }

    public Response(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        Results results,
        Confidence confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs) {
      this(
          apiVersion,
          requestId,
          status,
          translation,
          intent,
          queryIR,
          clarificationQuestions,
          results,
          confidence,
          evidence,
          suggestions,
          reranking,
          tookMs,
          null,
          Map.of(),
          List.of(),
          List.of());
    }

    public Response {
      clarificationQuestions =
          clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions);
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
      suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
      timings = timings == null ? Map.of() : Map.copyOf(timings);
      policies = policies == null ? List.of() : List.copyOf(policies);
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
      subqueries = subqueries == null ? List.of() : List.copyOf(subqueries);
      clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
    }
  }

  /** How retrieval was planned and executed ({@value Request#CONTRACT_V1} responses). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Retrieval(
      String requested,
      String executed,
      List<String> reasons,
      List<String> degradations,
      boolean degraded,
      int removedByValidation,
      List<String> providers,
      List<String> warnings,
      String rankingPolicy,
      String reranking,
      String outcome) {
    public Retrieval {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
      degradations = degradations == null ? List.of() : List.copyOf(degradations);
      providers = providers == null ? List.of() : List.copyOf(providers);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  /** A subjective preference understood from the query; never a filter. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Signal(String concept, String polarity, String quote) {}

  /** One constraint after mapping onto the schema. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConstraintStatus(
      String id, String field, String operator, String status, String note, String scope) {}

  /** The universal QueryIR interpretation of the query ({@code queryIrVersion}, e.g. 1.0). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Interpretation(
      String queryIrVersion,
      List<String> intents,
      List<String> retrievalNeeds,
      List<Signal> semanticSignals,
      List<ConstraintStatus> constraints,
      Map<String, Object> commerce) {
    public Interpretation {
      intents = intents == null ? List.of() : List.copyOf(intents);
      retrievalNeeds = retrievalNeeds == null ? List.of() : List.copyOf(retrievalNeeds);
      semanticSignals = semanticSignals == null ? List.of() : List.copyOf(semanticSignals);
      constraints = constraints == null ? List.of() : List.copyOf(constraints);
      commerce = commerce == null ? Map.of() : Map.copyOf(commerce);
    }
  }

  /** One part of a decomposed query and its own results. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubQuery(
      String id,
      String objective,
      String status,
      List<Constraint> constraints,
      Retrieval retrieval,
      Results results) {
    public SubQuery {
      constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }
  }

  /** A typed clarification question with its options. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Clarification(String kind, String field, String question, List<String> options) {
    public Clarification {
      options = options == null ? List.of() : List.copyOf(options);
    }
  }

  /** The typed error of a failed stream (QB-180): same codes and categories as REST errors. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StreamError(String code, String category, String message, boolean retryable) {}

  /**
   * A v2 progress event ({@code progress-event/2}). {@code sequence}, {@code terminal}, {@code
   * error} and {@code traceId} were added in 1.0 and are null from older servers.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public interface ProgressEvent {
    String kind();

    int version();

    Long sequence();

    Boolean terminal();

    StreamError error();

    /** Whether this event ends the stream: the server's flag, or the kind for older servers. */
    default boolean isTerminal() {
      return terminal() != null
          ? terminal()
          : kind().endsWith(".completed") || kind().endsWith(".failed");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Event(
      int version,
      String kind,
      String requestId,
      Response response,
      String message,
      Long sequence,
      Boolean terminal,
      StreamError error,
      String traceId)
      implements ProgressEvent {

    /** The 0.9 shape. */
    public Event(int version, String kind, String requestId, Response response, String message) {
      this(version, kind, requestId, response, message, null, null, null, null);
    }
  }

  /** {@code GET /api/v2/version}: API versions, contract versions and server limits. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ServerVersion(
      String name,
      String version,
      List<String> apiVersions,
      Map<String, String> contracts,
      Map<String, Object> limits) {
    public ServerVersion {
      apiVersions = apiVersions == null ? List.of() : List.copyOf(apiVersions);
      contracts = contracts == null ? Map.of() : Map.copyOf(contracts);
      limits = limits == null ? Map.of() : Map.copyOf(limits);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Policy(String pack, String version, String rule, String effect, String reason) {}

  public record SessionCreate(Integer ttlSeconds, String backend, List<String> lockedFields) {
    public SessionCreate {
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
    }
  }

  public record SessionTurn(
      String query, Integer expectedRevision, boolean resetContext, String mode, boolean rerank) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Turn(String requestId, String query, String status, Instant at) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Session(
      String id,
      String tenant,
      String subject,
      String backend,
      List<String> lockedFields,
      Instant createdAt,
      Instant expiresAt,
      int revision,
      Response last,
      List<Turn> turns) {
    public Session {
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
      turns = turns == null ? List.of() : List.copyOf(turns);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Proposal(
      String id, String description, String changedField, Instant expiresAt, String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SessionEvent(
      int version,
      String kind,
      String requestId,
      Session session,
      String message,
      Long sequence,
      Boolean terminal,
      StreamError error,
      String traceId)
      implements ProgressEvent {

    /** The 0.9 shape. */
    public SessionEvent(
        int version, String kind, String requestId, Session session, String message) {
      this(version, kind, requestId, session, message, null, null, null, null);
    }
  }
}
