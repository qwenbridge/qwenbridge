package io.qwenbridge.execution.provider.opensearch.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchClientTest {

    @Test
    void shouldExposeWebClient() {
        OpenSearchClient client = new OpenSearchClient(WebClient.builder().build());

        assertThat(client.webClient()).isNotNull();
    }
}