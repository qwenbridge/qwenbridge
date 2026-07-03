package io.qwenbridge.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIAnalysisCacheResponseTest {

    @Test
    void shouldNormalizeNullFields() {
        AIAnalysisCacheResponse response =
                AIAnalysisCacheResponse.builder()
                        .enabled(true)
                        .hit(false)
                        .miss(true)
                        .key(null)
                        .provider(null)
                        .model(null)
                        .version(null)
                        .build();

        assertThat(response.key()).isEmpty();
        assertThat(response.provider()).isEmpty();
        assertThat(response.model()).isEmpty();
        assertThat(response.version()).isEmpty();
    }
}
