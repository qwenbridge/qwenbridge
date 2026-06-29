package io.qwenbridge.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIAnalysisCacheResponseTest {

    @Test
    void shouldNormalizeNullFields() {
        AIAnalysisCacheResponse response =
                new AIAnalysisCacheResponse(
                        true,
                        false,
                        true,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(response.key()).isEmpty();
        assertThat(response.provider()).isEmpty();
        assertThat(response.model()).isEmpty();
        assertThat(response.version()).isEmpty();
    }
}
