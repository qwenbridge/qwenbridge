package io.qwenbridge.pipeline.step;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.threat.ThreatResult;
import io.qwenbridge.threat.ThreatService;
import io.qwenbridge.threat.model.ThreatAnalysis;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThreatStep implements PipelineStep<ThreatResult> {

    private final ThreatService threatService;

    public String name() {
        return "ThreatStep";
    }

    public int order() {
        return 20;
    }

    public Class<ThreatResult> resultType() {
        return ThreatResult.class;
    }

    public ThreatResult execute(ExecutionContext context) {
        NormalizedInput normalizedInput = context.get(NormalizedInput.class);
        String query = normalizedInput == null
                ? context.request().originalQuery()
                : normalizedInput.normalizedQuery();

        ThreatAnalysis analysis = threatService.analyzeDetailed(query);
        ThreatResult result = threatService.toResult(analysis);

        context.store(ThreatAnalysis.class, analysis);
        context.store(ThreatResult.class, result);

        return result;
    }
}
