package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.threat.ThreatResult;
import io.qwenbridge.threat.ThreatService;
import org.springframework.stereotype.Component;

@Component
public class ThreatStep implements PipelineStep<ThreatResult> {

    private final ThreatService threatService;

    public ThreatStep(ThreatService threatService) {
        this.threatService = threatService;
    }

    public String name() { return "ThreatStep"; }
    public int order() { return 30; }
    public Class<ThreatResult> resultType() { return ThreatResult.class; }

    public ThreatResult execute(ExecutionContext context) {
        return threatService.analyze(context.request().originalQuery());
    }
}
