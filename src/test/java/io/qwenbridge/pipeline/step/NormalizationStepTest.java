package io.qwenbridge.pipeline.step;

import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.service.DefaultInputNormalizer;
import io.qwenbridge.normalization.rule.UrlDecodeNormalizer;
import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationStepTest {

    @Test
    void shouldNormalizeRequestQuery() {
        NormalizationStep step = new NormalizationStep(
                new DefaultInputNormalizer(List.of(new UrlDecodeNormalizer()))
        );

        ExecutionContext context = new ExecutionContext("%3Cscript%3E");

        NormalizedInput result = step.execute(context);

        assertThat(result.originalQuery()).isEqualTo("%3Cscript%3E");
        assertThat(result.normalizedQuery()).isEqualTo("<script>");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void shouldExposeNormalizedInputAsResultType() {
        NormalizationStep step = new NormalizationStep(
                new DefaultInputNormalizer(List.of())
        );

        assertThat(step.name()).isEqualTo("NormalizationStep");
        assertThat(step.order()).isEqualTo(15);
        assertThat(step.resultType()).isEqualTo(NormalizedInput.class);
    }
}
