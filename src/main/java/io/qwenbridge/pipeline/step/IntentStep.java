package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import org.springframework.stereotype.Component;

@Component
public class IntentStep implements PipelineStep<IntentResult> {

    

    @Override
    public PipelineStage stage() {
        return PipelineStage.INTENT;
    }
@Override
    public String name() {
        return "IntentStep";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Class<IntentResult> resultType() {
        return IntentResult.class;
    }

    @Override
    public IntentResult execute(ExecutionContext context) {
        SearchAnalysis analysis = context.get(SearchAnalysis.class);

        if (analysis == null) {
            return IntentResult.unknown();
        }

        return analysis.toIntentResult();
    }
}
