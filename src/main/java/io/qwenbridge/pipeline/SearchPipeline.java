package io.qwenbridge.pipeline;

import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.model.ExecutionPlanResponse;
import io.qwenbridge.model.ExecutionResultResponse;
import io.qwenbridge.model.ExecutionStepResponse;
import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.pipeline.result.*;
import io.qwenbridge.threat.ThreatResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SearchPipeline {

    private final PipelineEngine pipelineEngine;

    public SearchPipeline(PipelineEngine pipelineEngine) {
        this.pipelineEngine = pipelineEngine;
    }

    public SearchAnalyzeResponse analyze(SearchAnalyzeRequest request) {
        ExecutionContext context = new ExecutionContext(request.query());

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

        DecisionType finalDecision = resolveFinalDecision(threat, policy, decision);
        double finalConfidence = resolveFinalConfidence(threat, policy, confidence);

        long processingTimeMs = Duration.between(
                requestContext.startedAt(),
                Instant.now()
        ).toMillis();

        return new SearchAnalyzeResponse(
                requestContext.requestId(),
                processingTimeMs,
                requestContext.originalQuery(),
                language.language(),
                intent.intent(),
                finalDecision,
                finalConfidence,
                rewrite.rewrites(),
                threat.reasons(),
                semantic.validated(),
                semantic.score(),
                policy.passed(),
                policy.violations(),
                toExecutionPlanResponse(executionPlan),
                toExecutionResultResponse(executionResult),
                context.trace()
        );
    }

    private ExecutionPlanResponse toExecutionPlanResponse(ExecutionPlanResult result) {
        if (result == null || !result.available()) {
            return ExecutionPlanResponse.unavailable();
        }

        return new ExecutionPlanResponse(
                true,
                result.plan().mode(),
                result.plan().backend(),
                result.plan().steps().stream()
                        .map(step -> new ExecutionStepResponse(
                                step.order(),
                                step.operation(),
                                step.reason()
                        ))
                        .toList(),
                result.plan().reason()
        );
    }

    private ExecutionResultResponse toExecutionResultResponse(ExecutionResultResult result) {
        if (result == null || !result.available()) {
            return ExecutionResultResponse.unavailable();
        }

        return new ExecutionResultResponse(
                true,
                result.result().executed(),
                result.result().operations(),
                result.result().results(),
                result.result().reason()
        );
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