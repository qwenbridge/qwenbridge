package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.SemanticResult;
import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticAmbiguity;
import io.qwenbridge.semantic.SemanticEntity;
import io.qwenbridge.semantic.SemanticEntityType;
import io.qwenbridge.semantic.SemanticService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticStepTest {

    @Test
    void shouldValidateSemanticUsingSemanticService() {
        SemanticService semanticService = query -> new SemanticAnalysis(
                query,
                "cheap iphone",
                "User wants an affordable iPhone.",
                List.of(new SemanticEntity("iphone", SemanticEntityType.PRODUCT, 0.95)),
                List.of("product_search"),
                SemanticAmbiguity.none(),
                0.9
        );

        ExecutionContext context = new ExecutionContext("cheap iphone");

        SemanticStep step = new SemanticStep(semanticService);

        SemanticResult result = step.execute(context);

        assertThat(result.validated()).isTrue();
        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.analysis()).isNotNull();
        assertThat(result.analysis().originalQuery()).isEqualTo("cheap iphone");
        assertThat(result.analysis().entities()).hasSize(1);
    }
}
