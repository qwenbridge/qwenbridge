package io.omnisearch.pipeline.step;

import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.IntentResult;
import io.omnisearch.pipeline.result.LanguageResult;
import io.omnisearch.pipeline.result.RewriteResult;
import io.omnisearch.rewrite.RewriteService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteStepTest {

    @Test
    void shouldRewritePersianTableQuery() {
        ExecutionContext context = new ExecutionContext("میز");
        context.store(LanguageResult.class, new LanguageResult("fa"));
        context.store(IntentResult.class, new IntentResult("PRODUCT_SEARCH"));

        RewriteStep step = new RewriteStep(new RewriteService());

        RewriteResult result = step.execute(context);

        assertThat(result.performed()).isTrue();
        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.rewrites())
                .containsExactly("desk", "table", "office desk");
    }

    @Test
    void shouldReturnOriginalQueryWhenNoRewriteExists() {
        ExecutionContext context = new ExecutionContext("chair");
        context.store(LanguageResult.class, new LanguageResult("en"));
        context.store(IntentResult.class, new IntentResult("PRODUCT_SEARCH"));

        RewriteStep step = new RewriteStep(new RewriteService());

        RewriteResult result = step.execute(context);

        assertThat(result.rewrites()).containsExactly("chair");
    }
}
