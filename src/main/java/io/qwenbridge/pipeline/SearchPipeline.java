package io.qwenbridge.pipeline;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.model.ExecutionPlanResponse;
import io.qwenbridge.model.ExecutionResultResponse;
import io.qwenbridge.model.AIAnalysisCacheResponse;
import io.qwenbridge.model.ExecutionStepResponse;
import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.model.SearchHitResponse;
import io.qwenbridge.model.SearchResultResponse;
import io.qwenbridge.pipeline.result.*;
import io.qwenbridge.threat.ThreatResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SearchPipeline {

    private final PipelineEngine pipelineEngine;

    public SearchAnalyzeResponse analyze(SearchAnalyzeRequest request) {
        ExecutionContext context = new ExecutionContext(request.requestId(), request.query());

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

        long processingTimeMs = Duration.between(
                requestContext.startedAt(),
                Instant.now()
        ).toMillis();

        return SearchAnalyzeResponse.builder()
                .requestId(requestContext.requestId())
                .processingTimeMs(processingTimeMs)
                .originalQuery(requestContext.originalQuery())
                .language(language.language())
                .intent(intent.intent())
                .decision(finalDecision)
                .confidence(finalConfidence)
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
                .build();
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
                .steps(result.plan().steps().stream()
                        .map(step -> new ExecutionStepResponse(
                                step.order(),
                                step.operation(),
                                step.reason()
                        ))
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
                .hits(response.results()
                        .hits()
                        .stream()
                        .map(hit -> SearchHitResponse.builder()
                                .id(hit.id())
                                .score(hit.score())
                                .document(hit.document())
                                .build())
                        .toList())
                .build();
    }

    private DecisionType resolveFinalDecision(
            ThreatResult threat,
            PolicyResult policy,
            DecisionResult decision
    ) {
        if (!threat.safe() || !policy.passed()) {
            return DecisionType.BLOCK;
        }
        return decision.type();
    }

    private double resolveFinalConfidence(
            ThreatResult threat,
            PolicyResult policy,
            ConfidenceResult confidence
    ) {
        if (!threat.safe() || !policy.passed()) {
            return 1.0;
        }
        return confidence.value();
    }
}