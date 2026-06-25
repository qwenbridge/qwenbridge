package io.qwenbridge.pipeline.step;

import io.qwenbridge.intent.IntentService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
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
