package io.omnisearch.pipeline.step;

import io.omnisearch.intent.IntentService;
import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.IntentResult;
import org.springframework.stereotype.Component;

@Component
public class IntentStep implements PipelineStep<IntentResult> {

    private final IntentService intentService;

    public IntentStep(IntentService intentService) {
        this.intentService = intentService;
    }

    public String name() { return "IntentStep"; }
    public int order() { return 20; }
    public Class<IntentResult> resultType() { return IntentResult.class; }

    public IntentResult execute(ExecutionContext context) {
        return new IntentResult(
                intentService.detect(context.request().originalQuery())
        );
    }
}
