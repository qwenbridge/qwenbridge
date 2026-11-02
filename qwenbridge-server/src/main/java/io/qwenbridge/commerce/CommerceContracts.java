package io.qwenbridge.commerce;

import io.qwenbridge.analysis.model.Grounded.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned commerce contracts. Evidence offsets always refer to the named source text. */
public final class CommerceContracts {
  private CommerceContracts() {}

  public enum ValueType {
    TEXT,
    DECIMAL,
    BOOLEAN,
    DATE
  }

  public enum Normalization {
    NONE,
    LOWERCASE,
    UPPERCASE
  }

  public record Field(
      ValueType type, Set<Operator> operators, String indexField, Normalization normalization) {
    public Field(ValueType type, Set<Operator> operators, String indexField) {
      this(type, operators, indexField, Normalization.NONE);
    }

    public Field {
      normalization = normalization == null ? Normalization.NONE : normalization;
      operators = Set.copyOf(operators);
      if (type == null
          || operators.isEmpty()
          || indexField == null
          || !indexField.matches("[A-Za-z_][A-Za-z0-9_.]*"))
        throw new IllegalArgumentException("Invalid schema field");
    }
  }

  public record Schema(String id, int version, Map<String, Field> fields) {
    public Schema {
      if (id == null
          || !id.matches("[a-z][a-z0-9-]{0,63}")
          || version < 1
          || fields == null
          || fields.isEmpty()) throw new IllegalArgumentException("Invalid schema");
      fields = Map.copyOf(fields);
      if (fields.size() > 64) throw new IllegalArgumentException("Too many schema fields");
    }
  }

  public record Plan(
      int version,
      String schemaId,
      int schemaVersion,
      String tenant,
      List<Constraint> constraints,
      Map<String, Field> fields) {
    public Plan {
      constraints = List.copyOf(constraints);
      fields = Map.copyOf(fields);
      if (version != 1 || tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,64}"))
        throw new IllegalArgumentException("Invalid plan");
    }
  }

  public record Translation(
      int version,
      String original,
      String sourceLanguage,
      String targetLanguage,
      String translated,
      boolean constraintsPreserved,
      List<String> issues) {
    public Translation {
      issues = List.copyOf(issues);
    }
  }

  public record SearchRequest(
      String requestId,
      String query,
      String catalogLanguage,
      String schemaId,
      Integer schemaVersion,
      String mode,
      List<Constraint> retainedConstraints,
      boolean rerank,
      String backend,
      Integer limit,
      String cursor,
      List<String> lockedFields,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String contract) {
    /**
     * QB-085: the versioned Commerce Search Contract. Without {@code contract} a request keeps the
     * apiVersion 2 behaviour. With it, the request is interpreted over the universal QueryIR and
     * the retrieval strategy is chosen by the planner, never by the client.
     */
    public static final String CONTRACT_V1 = "commerce-search/1.0";

    public SearchRequest(
        String requestId,
        String query,
        String catalogLanguage,
        String schemaId,
        Integer schemaVersion,
        String mode,
        List<Constraint> retainedConstraints,
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
          retainedConstraints,
          rerank,
          backend,
          limit,
          cursor,
          lockedFields,
          null);
    }

    public SearchRequest(
        String requestId,
        String query,
        String catalogLanguage,
        String schemaId,
        Integer schemaVersion,
        String mode,
        List<Constraint> retainedConstraints,
        boolean rerank) {
      this(
          requestId,
          query,
          catalogLanguage,
          schemaId,
          schemaVersion,
          mode,
          retainedConstraints,
          rerank,
          null,
          10,
          null,
          List.of());
    }

    public SearchRequest {
      if (query == null || query.isBlank() || query.length() > 4096)
        throw new IllegalArgumentException("Query must contain 1..4096 characters");
      limit = limit == null ? Integer.valueOf(10) : limit;
      if (limit < 1 || limit > 100) throw new IllegalArgumentException("Page size must be 1..100");
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
      if (lockedFields.size() > 32) throw new IllegalArgumentException("Too many locked fields");
      // QB-085: the contract no longer hard-codes the backend list; the registry of configured
      // connectors decides (CommerceBackends), so a new connector needs no contract change.
      if (backend != null && !backend.matches("[a-z][a-z0-9-]{0,31}"))
        throw new IllegalArgumentException("Unknown backend");
      if (cursor != null && cursor.length() > 4096)
        throw new IllegalArgumentException("Invalid page cursor");
      catalogLanguage = catalogLanguage == null ? "en" : catalogLanguage;
      if (!catalogLanguage.matches("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"))
        throw new IllegalArgumentException("Invalid catalog language");
      schemaId = schemaId == null ? "commerce" : schemaId;
      schemaVersion = schemaVersion == null ? Integer.valueOf(1) : schemaVersion;
      if (contract != null && !CONTRACT_V1.equals(contract))
        throw new IllegalArgumentException("Unsupported contract version");
      if (contract != null) {
        // The planner chooses the strategy from the request's needs and provider capabilities.
        if (mode != null && !"AUTO".equals(mode))
          throw new IllegalArgumentException(
              "The " + CONTRACT_V1 + " contract chooses the retrieval strategy; omit mode");
        mode = "AUTO";
      } else {
        mode = mode == null ? "KEYWORD" : mode;
        if (!Set.of("KEYWORD", "VECTOR", "HYBRID").contains(mode))
          throw new IllegalArgumentException("Unsupported search mode");
      }
      retainedConstraints =
          retainedConstraints == null ? List.of() : List.copyOf(retainedConstraints);
    }

    public boolean planned() {
      return contract != null;
    }

    /**
     * This request with {@code id} as its request ID; everything else, the contract included, kept.
     */
    public SearchRequest withRequestId(String id) {
      return new SearchRequest(
          id,
          query,
          catalogLanguage,
          schemaId,
          schemaVersion,
          mode,
          retainedConstraints,
          rerank,
          backend,
          limit,
          cursor,
          lockedFields,
          contract);
    }
  }

  public record Attribution(
      String id,
      String tenant,
      String requestId,
      String source,
      Instant occurredAt,
      Instant observedAt,
      String claim,
      List<Evidence> spans,
      String parentId) {
    public Attribution {
      spans = List.copyOf(spans);
    }
  }

  public record PolicyDecision(
      String pack, String version, String rule, String effect, String reason) {}

  public record SearchResponse(
      int apiVersion,
      String requestId,
      String status,
      Translation translation,
      String intent,
      Plan queryIR,
      List<String> clarificationQuestions,
      io.qwenbridge.execution.provider.model.SearchResultSet results,
      io.qwenbridge.confidence.ConfidenceAssessment confidence,
      List<Attribution> evidence,
      List<String> suggestions,
      String reranking,
      long tookMs,
      String nextCursor,
      Map<String, Long> timings,
      List<PolicyDecision> policies,
      List<String> lockedFields,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String contract,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          Retrieval retrieval,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          Interpretation interpretation,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          List<SubQueryResult> subqueries,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          List<ClarificationQuestion> clarifications,
      // QB-291: typo processing, present only once typo correction is enabled.
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          io.qwenbridge.spelling.SpellingView spelling,
      // QB-286: multilingual expansion, present only once expansion is enabled.
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          io.qwenbridge.multilingual.ExpansionView expansion) {
    /** A response without multilingual expansion. */
    public SearchResponse(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        io.qwenbridge.execution.provider.model.SearchResultSet results,
        io.qwenbridge.confidence.ConfidenceAssessment confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs,
        String nextCursor,
        Map<String, Long> timings,
        List<PolicyDecision> policies,
        List<String> lockedFields,
        String contract,
        Retrieval retrieval,
        Interpretation interpretation,
        List<SubQueryResult> subqueries,
        List<ClarificationQuestion> clarifications,
        io.qwenbridge.spelling.SpellingView spelling) {
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

    public SearchResponse(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        io.qwenbridge.execution.provider.model.SearchResultSet results,
        io.qwenbridge.confidence.ConfidenceAssessment confidence,
        List<Attribution> evidence,
        List<String> suggestions,
        String reranking,
        long tookMs,
        String nextCursor,
        Map<String, Long> timings,
        List<PolicyDecision> policies,
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
          null,
          null,
          null);
    }

    public SearchResponse(
        int apiVersion,
        String requestId,
        String status,
        Translation translation,
        String intent,
        Plan queryIR,
        List<String> clarificationQuestions,
        io.qwenbridge.execution.provider.model.SearchResultSet results,
        io.qwenbridge.confidence.ConfidenceAssessment confidence,
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

    public SearchResponse {
      timings = Map.copyOf(timings);
      policies = List.copyOf(policies);
      lockedFields = List.copyOf(lockedFields);
      clarificationQuestions = List.copyOf(clarificationQuestions);
      evidence = List.copyOf(evidence);
      suggestions = List.copyOf(suggestions);
      subqueries = subqueries == null ? null : List.copyOf(subqueries);
      clarifications = clarifications == null ? null : List.copyOf(clarifications);
    }

    /** The same response with other results (used to apply feedback after execution). */
    public SearchResponse withResults(io.qwenbridge.execution.provider.model.SearchResultSet r) {
      return new SearchResponse(
          apiVersion,
          requestId,
          status,
          translation,
          intent,
          queryIR,
          clarificationQuestions,
          r,
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
          expansion);
    }

    /** The same response with multilingual expansion attached (QB-286). */
    public SearchResponse withExpansion(io.qwenbridge.multilingual.ExpansionView value) {
      return new SearchResponse(
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
          value);
    }

    /** The same response with typo processing attached (QB-291). */
    public SearchResponse withSpelling(io.qwenbridge.spelling.SpellingView value) {
      return new SearchResponse(
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
          value,
          expansion);
    }
  }

  /**
   * QB-090: how retrieval actually ran for a {@code commerce-search/1.0} request.
   *
   * @param requested the strategy the planner wanted for the query's needs
   * @param executed what ran; differs from {@code requested} only with a recorded reason
   * @param reasons planner reason codes (QB-101)
   * @param degradations runtime degradations, e.g. an unavailable embedding service
   * @param degraded whether anything planned or requested did not run
   * @param removedByValidation results dropped because they violated a HARD constraint
   * @param providers providers that took part
   * @param rankingPolicy the ranking policy {@code id@version} (QB-117)
   * @param reranking {@code none}, the reranker that ordered the top results, or why the baseline
   *     was kept (QB-119, QB-120)
   * @param outcome RESULTS, LOW_RESULTS, ZERO_RESULTS, PROVIDER_FAILED or NOT_EXECUTED (QB-125);
   *     zero results from a failed provider are never reported as an empty catalog
   */
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
      reasons = List.copyOf(reasons);
      degradations = List.copyOf(degradations);
      providers = List.copyOf(providers);
      warnings = List.copyOf(warnings);
    }
  }

  /**
   * A targeted clarification question (QB-135): what is unclear, about which field, and the answers
   * that would resolve it. {@code kind} is CONFLICT, MISSING_QUALIFIER, UNSUPPORTED, INVALID_VALUE
   * or VAGUE.
   */
  public record ClarificationQuestion(
      String kind, String field, String question, List<String> options) {
    public ClarificationQuestion {
      options = List.copyOf(options);
    }
  }

  /** A subjective preference understood from the query (QB-088); never a filter. */
  public record Signal(String concept, String polarity, String quote) {}

  /** Status of one constraint after mapping onto the Commerce schema (QB-086). */
  public record ConstraintStatus(
      String id, String field, String operator, String status, String note, String scope) {}

  /**
   * QB-086: the universal QueryIR as the Commerce pack interpreted it: intents, semantic signals,
   * the schema validation of every constraint and the decomposition.
   */
  public record Interpretation(
      String queryIrVersion,
      List<String> intents,
      List<String> retrievalNeeds,
      List<Signal> semanticSignals,
      List<ConstraintStatus> constraints,
      Map<String, Object> commerce) {
    public Interpretation {
      commerce = commerce == null ? Map.of() : Map.copyOf(commerce);
      intents = List.copyOf(intents);
      retrievalNeeds = List.copyOf(retrievalNeeds);
      semanticSignals = List.copyOf(semanticSignals);
      constraints = List.copyOf(constraints);
    }
  }

  /** QB-089: one part of a decomposed multi-product query, executed on its own. */
  public record SubQueryResult(
      String id,
      String objective,
      String status,
      List<Constraint> constraints,
      Retrieval retrieval,
      io.qwenbridge.execution.provider.model.SearchResultSet results) {
    public SubQueryResult {
      constraints = List.copyOf(constraints);
    }
  }
}
