package io.qwenbridge.pipeline;

import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.pipeline.result.*;
import io.qwenbridge.threat.ThreatResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
                context.trace()
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
