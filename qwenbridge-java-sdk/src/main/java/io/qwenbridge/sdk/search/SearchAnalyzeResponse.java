package io.qwenbridge.sdk.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Tolerates fields added by newer servers (forward compatibility). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchAnalyzeResponse(
    String requestId,
    long processingTimeMs,
    String originalQuery,
    String language,
    String intent,
    String decision,
    double confidence,
    JsonNode confidenceAssessment,
    List<String> rewrites,
    List<String> threatReasons,
    boolean semanticValidated,
    double semanticScore,
    boolean policyPassed,
    List<String> policyViolations,
    JsonNode executionPlan,
    JsonNode executionResult,
    JsonNode search,
    JsonNode cache,
    JsonNode pipelineTrace,
    io.qwenbridge.sdk.spelling.Spelling spelling,
    io.qwenbridge.sdk.expansion.Expansion expansion) {

  /** The shape without multilingual expansion (before QB-286). */
  public SearchAnalyzeResponse(
      String requestId,
      long processingTimeMs,
      String originalQuery,
      String language,
      String intent,
      String decision,
      double confidence,
      JsonNode confidenceAssessment,
      List<String> rewrites,
      List<String> threatReasons,
      boolean semanticValidated,
      double semanticScore,
      boolean policyPassed,
      List<String> policyViolations,
      JsonNode executionPlan,
      JsonNode executionResult,
      JsonNode search,
      JsonNode cache,
      JsonNode pipelineTrace,
      io.qwenbridge.sdk.spelling.Spelling spelling) {
    this(
        requestId,
        processingTimeMs,
        originalQuery,
        language,
        intent,
        decision,
        confidence,
        confidenceAssessment,
        rewrites,
        threatReasons,
        semanticValidated,
        semanticScore,
        policyPassed,
        policyViolations,
        executionPlan,
        executionResult,
        search,
        cache,
        pipelineTrace,
        spelling,
        null);
  }

  /** The shape without typo processing (before QB-291). */
  public SearchAnalyzeResponse(
      String requestId,
      long processingTimeMs,
      String originalQuery,
      String language,
      String intent,
      String decision,
      double confidence,
      JsonNode confidenceAssessment,
      List<String> rewrites,
      List<String> threatReasons,
      boolean semanticValidated,
      double semanticScore,
      boolean policyPassed,
      List<String> policyViolations,
      JsonNode executionPlan,
      JsonNode executionResult,
      JsonNode search,
      JsonNode cache,
      JsonNode pipelineTrace) {
    this(
        requestId,
        processingTimeMs,
        originalQuery,
        language,
        intent,
        decision,
        confidence,
        confidenceAssessment,
        rewrites,
        threatReasons,
        semanticValidated,
        semanticScore,
        policyPassed,
        policyViolations,
        executionPlan,
        executionResult,
        search,
        cache,
        pipelineTrace,
        null,
        null);
  }

  public SearchAnalyzeResponse {
    rewrites = rewrites == null ? List.of() : List.copyOf(rewrites);
    threatReasons = threatReasons == null ? List.of() : List.copyOf(threatReasons);
    policyViolations = policyViolations == null ? List.of() : List.copyOf(policyViolations);
  }
}
