package io.qwenbridge.execution.provider.opensearch.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class OpenSearchClient {

    private final WebClient openSearchWebClient;

    public OpenSearchClient(WebClient openSearchWebClient) {
        this.openSearchWebClient = openSearchWebClient;
    }

    public Map<String, Object> search(String index, Map<String, Object> query) {
        return openSearchWebClient
                .post()
                .uri("/{index}/_search", index)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public WebClient webClient() {
        return openSearchWebClient;
    }
}