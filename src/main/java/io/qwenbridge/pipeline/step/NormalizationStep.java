package io.qwenbridge.pipeline.step;

import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.service.InputNormalizer;
import io.qwenbridge.pipeline.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class NormalizationStep implements PipelineStep<NormalizedInput> {

    private final InputNormalizer inputNormalizer;

    public NormalizationStep(InputNormalizer inputNormalizer) {
        this.inputNormalizer = inputNormalizer;
    }

    @Override
    public String name() {
        return "NormalizationStep";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public Class<NormalizedInput> resultType() {
        return NormalizedInput.class;
    }

    @Override
    public NormalizedInput execute(ExecutionContext context) {
        return inputNormalizer.normalize(context.request().originalQuery());
    }
}
