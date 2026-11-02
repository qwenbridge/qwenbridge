package io.qwenbridge.model;

import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.pipeline.result.PipelineTraceItem;
import java.util.List;
import lombok.Builder;

@Builder
public record SearchAnalyzeResponse(
    String requestId,
    long processingTimeMs,
    String originalQuery,
    String language,
    String intent,
    DecisionType decision,
    double confidence,
    io.qwenbridge.confidence.ConfidenceAssessment confidenceAssessment,
    List<String> rewrites,
    List<String> threatReasons,
    boolean semanticValidated,
    double semanticScore,
    boolean policyPassed,
    List<String> policyViolations,
    ExecutionPlanResponse executionPlan,
    ExecutionResultResponse executionResult,
    SearchResultResponse search,
    AIAnalysisCacheResponse cache,
    List<PipelineTraceItem> pipelineTrace,
    List<String> clarificationQuestions,
    LanguageAnalysisResponse languageAnalysis,
    IntentAnalysisResponse intentAnalysis,
    java.util.Map<String, Object> queryIR,
    List<UnresolvedView> unresolved,
    ConstraintValidationResponse constraintValidation,
    TranslationResponse translation,
    TextRepresentationsResponse textRepresentations,
    ThreatAssessmentResponse threatAssessment,
    UnderstandingResponses.ConfidenceReportResponse confidenceReport,
    UnderstandingResponses.ExplanationsResponse explanations,
    // QB-291: typo processing, present only once typo correction is enabled.
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        io.qwenbridge.spelling.SpellingView spelling,
    // QB-286: multilingual expansion, present only once expansion is enabled.
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        io.qwenbridge.multilingual.ExpansionView expansion) {
  public SearchAnalyzeResponse {
    rewrites = rewrites == null ? List.of() : List.copyOf(rewrites);
    threatReasons = threatReasons == null ? List.of() : List.copyOf(threatReasons);
    policyViolations = policyViolations == null ? List.of() : List.copyOf(policyViolations);
    pipelineTrace = pipelineTrace == null ? List.of() : List.copyOf(pipelineTrace);
    clarificationQuestions =
        clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions);
    queryIR = queryIR == null ? null : java.util.Map.copyOf(queryIR);
    unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
  }
}
