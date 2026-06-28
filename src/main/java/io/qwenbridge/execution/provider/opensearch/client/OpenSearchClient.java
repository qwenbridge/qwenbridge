package io.qwenbridge.execution.provider.opensearch.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OpenSearchClient {

    private final WebClient openSearchWebClient;

    public OpenSearchClient(WebClient openSearchWebClient) {
        this.openSearchWebClient = openSearchWebClient;
    }

    public WebClient webClient() {
        return openSearchWebClient;
    }
}