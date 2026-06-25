package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import io.qwenbridge.rewrite.RewriteService;
import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteStepTest {

    @Test
    void shouldRewriteQueryUsingAIRewriteService() {
        AIRewriteService aiRewriteService = query -> "table";
        RewriteStep step = new RewriteStep(new RewriteService(aiRewriteService));

        RewriteResult result = step.execute(context("tabel"));

        assertThat(result.performed()).isTrue();
        assertThat(result.provider()).isEqualTo("ai");
        assertThat(result.rewrites()).containsExactly("table");
    }

    @Test
    void shouldKeepOriginalQueryWhenAIReturnsBlank() {
        AIRewriteService aiRewriteService = query -> " ";
        RewriteStep step = new RewriteStep(new RewriteService(aiRewriteService));

        RewriteResult result = step.execute(context("table"));

        assertThat(result.performed()).isTrue();
        assertThat(result.provider()).isEqualTo("ai");
        assertThat(result.rewrites()).containsExactly("table");
    }

    @Test
    void shouldFallbackToOriginalQueryWhenAIRewriteFails() {
        AIRewriteService aiRewriteService = query -> {
            throw new RuntimeException("ollama unavailable");
        };

        RewriteStep step = new RewriteStep(new RewriteService(aiRewriteService));

        RewriteResult result = step.execute(context("tabel"));

        assertThat(result.performed()).isTrue();
        assertThat(result.provider()).isEqualTo("ai");
        assertThat(result.rewrites()).containsExactly("tabel");
    }

    private static ExecutionContext context(String query) {
        ExecutionContext context = new ExecutionContext(query);

        context.store(LanguageResult.class, new LanguageResult("en"));
        context.store(IntentResult.class, new IntentResult("PRODUCT_SEARCH"));

        return context;
    }
}
