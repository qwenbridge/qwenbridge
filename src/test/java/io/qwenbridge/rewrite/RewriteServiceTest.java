package io.qwenbridge.rewrite;

import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteServiceTest {

    @Test
    void shouldReturnAIRewrite() {
        AIRewriteService aiRewriteService = query -> "table";
        RewriteService service = new RewriteService(aiRewriteService);

        assertThat(service.rewrite("tabel", "en", "PRODUCT_SEARCH"))
                .containsExactly("table");
    }

    @Test
    void shouldFallbackToOriginalQueryWhenAIReturnsBlank() {
        AIRewriteService aiRewriteService = query -> " ";
        RewriteService service = new RewriteService(aiRewriteService);

        assertThat(service.rewrite("table", "en", "PRODUCT_SEARCH"))
                .containsExactly("table");
    }

    @Test
    void shouldFallbackToOriginalQueryWhenAIThrowsException() {
        AIRewriteService aiRewriteService = query -> {
            throw new RuntimeException("ollama unavailable");
        };

        RewriteService service = new RewriteService(aiRewriteService);

        assertThat(service.rewrite("tabel", "en", "PRODUCT_SEARCH"))
                .containsExactly("tabel");
    }
}
