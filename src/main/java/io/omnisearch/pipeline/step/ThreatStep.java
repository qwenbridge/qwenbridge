package io.omnisearch.pipeline.step;

import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.threat.ThreatResult;
import io.omnisearch.threat.ThreatService;
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
