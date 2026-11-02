package io.qwenbridge.pipeline;

import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.confidence.ConfidenceAssessment;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.input.model.InputSource;
import io.qwenbridge.input.model.MultilingualInput;
import io.qwenbridge.model.AIAnalysisCacheResponse;
import io.qwenbridge.model.ExecutionPlanResponse;
import io.qwenbridge.model.ExecutionResultResponse;
import io.qwenbridge.model.ExecutionStepResponse;
import io.qwenbridge.model.LanguageAnalysisResponse;
import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.model.SearchHitResponse;
import io.qwenbridge.model.SearchResultResponse;
import io.qwenbridge.pipeline.result.*;
import io.qwenbridge.threat.ThreatResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class SearchPipeline {

  private final PipelineEngine pipelineEngine;

  public SearchAnalyzeResponse analyze(SearchAnalyzeRequest request) {

    MultilingualInput input = toMultilingualInput(request);
    ExecutionContext context = new ExecutionContext(request.requestId(), input);
    context.store(
        io.qwenbridge.pipeline.result.RequestHints.class,
        new io.qwenbridge.pipeline.result.RequestHints(request.domainHints(), request.context()));

    pipelineEngine.execute(context);

    RequestContext requestContext = context.request();
    LanguageResult language = context.get(LanguageResult.class);
    IntentResult intent = context.get(IntentResult.class);
    ThreatResult threat = context.get(ThreatResult.class);
    RewriteResult rewrite = context.get(RewriteResult.class);
    SemanticResult semantic = context.get(SemanticResult.class);
    PolicyResult policy = context.get(PolicyResult.class);
    ConfidenceResult confidence = context.get(ConfidenceResult.class);
    DecisionResult decision = context.get(DecisionResult.class);
    ExecutionPlanResult executionPlan = context.get(ExecutionPlanResult.class);
    ExecutionResultResult executionResult = context.get(ExecutionResultResult.class);
    AIAnalysisCacheTrace cacheTrace = context.get(AIAnalysisCacheTrace.class);

    DecisionType finalDecision = resolveFinalDecision(threat, policy, decision);
    double finalConfidence = resolveFinalConfidence(threat, policy, confidence);

    long processingTimeMs = Duration.between(requestContext.startedAt(), Instant.now()).toMillis();

    return SearchAnalyzeResponse.builder()
        .requestId(requestContext.requestId())
        .processingTimeMs(processingTimeMs)
        .originalQuery(requestContext.originalQuery())
        .language(language.language())
        .languageAnalysis(LanguageAnalysisResponse.from(language.analysis()))
        .intentAnalysis(
            io.qwenbridge.model.IntentAnalysisResponse.from(
                context.get(io.qwenbridge.core.intent.IntentUnderstanding.class)))
        .queryIR(queryIrView(context))
        .unresolved(
            java.util.Optional.ofNullable(
                    context.get(io.qwenbridge.core.extraction.Extraction.class))
                .map(
                    e ->
                        e.unresolved().stream()
                            .map(io.qwenbridge.model.UnresolvedView::from)
                            .toList())
                .orElse(java.util.List.of()))
        .constraintValidation(
            io.qwenbridge.model.ConstraintValidationResponse.from(
                context.get(io.qwenbridge.pipeline.result.ConstraintValidationResult.class)))
        .translation(
            io.qwenbridge.model.TranslationResponse.from(context.get(TranslationStepResult.class)))
        .textRepresentations(
            io.qwenbridge.model.TextRepresentationsResponse.from(
                context.get(TranslationStepResult.class)))
        .threatAssessment(
            io.qwenbridge.model.ThreatAssessmentResponse.from(
                context.get(io.qwenbridge.core.threat.ThreatAssessment.class)))
        .confidenceReport(
            io.qwenbridge.model.UnderstandingResponses.ConfidenceReportResponse.from(
                io.qwenbridge.explanation.UnderstandingReports.confidence(context)))
        .explanations(
            io.qwenbridge.model.UnderstandingResponses.ExplanationsResponse.from(
                io.qwenbridge.explanation.UnderstandingReports.explanations(
                    context, requestContext.startedAt())))
        .intent(intent.intent())
        .decision(finalDecision)
        .confidence(finalConfidence)
        .confidenceAssessment(resolveConfidenceAssessment(context, threat, policy))
        .rewrites(rewrite.rewrites())
        .threatReasons(threat.reasons())
        .semanticValidated(semantic.validated())
        .semanticScore(semantic.score())
        .policyPassed(policy.passed())
        .policyViolations(policy.violations())
        .executionPlan(toExecutionPlanResponse(executionPlan))
        .executionResult(toExecutionResultResponse(executionResult))
        .search(toSearchResultResponse(context))
        .cache(toCacheResponse(cacheTrace))
        .pipelineTrace(context.trace())
        .spelling(
            io.qwenbridge.spelling.SpellingView.of(
                context.get(io.qwenbridge.core.spelling.CorrectionResult.class)))
        .expansion(
            io.qwenbridge.multilingual.ExpansionView.of(
                context.get(io.qwenbridge.core.expansion.ExpansionResult.class)))
        .clarificationQuestions(
            finalDecision == DecisionType.CLARIFY
                ? (context.get(io.qwenbridge.pipeline.step.DecisionStep.ConstraintQuestions.class)
                        != null
                    ? context
                        .get(io.qwenbridge.pipeline.step.DecisionStep.ConstraintQuestions.class)
                        .questions()
                    : context
                            .get(io.qwenbridge.analysis.model.SearchAnalysis.class)
                            .ambiguities()
                            .isEmpty()
                        ? java.util.List.of("Please clarify which product or comparison you want.")
                        : context
                            .get(io.qwenbridge.analysis.model.SearchAnalysis.class)
                            .ambiguities())
                : java.util.List.of())
        .build();
  }

  /** Keeps exact decimals: money and thresholds must not become binary floating point. */
  private static final com.fasterxml.jackson.databind.ObjectMapper IR_MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper()
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  /** The assembled universal QueryIR in its canonical JSON shape, or null when not available. */
  @SuppressWarnings("unchecked")
  private static java.util.Map<String, Object> queryIrView(ExecutionContext context) {
    io.qwenbridge.pipeline.result.QueryIrResult result =
        context.get(io.qwenbridge.pipeline.result.QueryIrResult.class);
    if (result == null || result.ir().isEmpty()) {
      return null;
    }
    return IR_MAPPER.convertValue(
        io.qwenbridge.core.queryir.QueryIrJson.toTree(result.ir().get()), java.util.Map.class);
  }

  MultilingualInput toMultilingualInput(SearchAnalyzeRequest request) {
    return MultilingualInput.of(
        request.query(),
        request.declaredLanguage(),
        parseLocale(request.locale()),
        InputSource.API);
  }

  private AIAnalysisCacheResponse toCacheResponse(AIAnalysisCacheTrace trace) {
    if (trace == null) {
      return AIAnalysisCacheResponse.builder()
          .enabled(false)
          .hit(false)
          .miss(true)
          .key("")
          .provider("")
          .model("")
          .version("")
          .build();
    }

    return AIAnalysisCacheResponse.builder()
        .enabled(trace.enabled())
        .hit(trace.hit())
        .miss(trace.miss())
        .key(trace.key())
        .provider(trace.provider())
        .model(trace.model())
        .version(trace.version())
        .build();
  }

  private ExecutionPlanResponse toExecutionPlanResponse(ExecutionPlanResult result) {
    if (result == null || !result.available()) {
      return ExecutionPlanResponse.unavailable();
    }

    return ExecutionPlanResponse.builder()
        .available(true)
        .mode(result.plan().mode())
        .backend(result.plan().backend())
        .steps(
            result.plan().steps().stream()
                .map(
                    step ->
                        new ExecutionStepResponse(step.order(), step.operation(), step.reason()))
                .toList())
        .reason(result.plan().reason())
        .build();
  }

  private ExecutionResultResponse toExecutionResultResponse(ExecutionResultResult result) {
    if (result == null || !result.available()) {
      return ExecutionResultResponse.unavailable();
    }

    return ExecutionResultResponse.builder()
        .available(true)
        .executed(result.result().executed())
        .operations(result.result().operations())
        .results(result.result().results())
        .reason(result.result().reason())
        .build();
  }

  private SearchResultResponse toSearchResultResponse(ExecutionContext context) {
    SearchResponse response = context.get(SearchResponse.class);

    if (response == null) {
      return SearchResultResponse.unavailable();
    }

    return SearchResultResponse.builder()
        .available(true)
        .totalHits(response.results().totalHits())
        .tookMillis(response.results().tookMillis())
        .hits(
            response.results().hits().stream()
                .map(
                    hit ->
                        SearchHitResponse.builder()
                            .id(hit.id())
                            .score(hit.score())
                            .document(hit.document())
                            .build())
                .toList())
        .build();
  }

  private DecisionType resolveFinalDecision(
      ThreatResult threat, PolicyResult policy, DecisionResult decision) {
    if (!threat.safe() || !policy.passed()) {
      return DecisionType.BLOCK;
    }
    return decision.type();
  }

  private double resolveFinalConfidence(
      ThreatResult threat, PolicyResult policy, ConfidenceResult confidence) {
    if (!threat.safe() || !policy.passed()) {
      return 1.0;
    }
    return confidence.value();
  }

  private ConfidenceAssessment resolveConfidenceAssessment(
      ExecutionContext context, ThreatResult threat, PolicyResult policy) {
    if (!threat.safe() || !policy.passed()) {
      return new ConfidenceAssessment(
          1.0,
          "deterministic-safety-decision-v1",
          false,
          java.util.Map.of(
              "threatBlocked",
              threat.safe() ? 0.0 : 1.0,
              "policyBlocked",
              policy.passed() ? 0.0 : 1.0));
    }
    ConfidenceAssessment assessment = context.get(ConfidenceAssessment.class);
    return assessment == null
        ? new ConfidenceAssessment(0.0, "unavailable", false, java.util.Map.of())
        : assessment;
  }

  private Locale parseLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return null;
    }

    return Locale.forLanguageTag(locale.trim());
  }
}
