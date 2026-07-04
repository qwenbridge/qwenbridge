package io.qwenbridge.event.snapshot;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.threat.ThreatResult;
import org.springframework.stereotype.Component;

@Component
public class PipelineContextSnapshotFactory {

    public PipelineContextSnapshot create(ExecutionContext context) {

        ThreatResult threat = context.get(ThreatResult.class);
        LanguageResult language = context.get(LanguageResult.class);
        IntentResult intent = context.get(IntentResult.class);
        DecisionResult decision = context.get(DecisionResult.class);

        return new PipelineContextSnapshot(
                context.request().requestId(),
                context.request().originalQuery(),
                context.stopped(),
                threat == null || threat.safe(),
                language == null ? "unknown" : language.language(),
                intent == null || intent.intent() == null ? "UNKNOWN" : intent.intent(),
                decision == null || decision.type() == null ? "NONE" : decision.type().name(),
                System.currentTimeMillis()
        );
    }
}