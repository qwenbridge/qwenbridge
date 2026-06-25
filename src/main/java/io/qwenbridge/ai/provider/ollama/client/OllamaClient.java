package io.qwenbridge.ai.provider.ollama.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OllamaClient {

    private static final Logger log =
            LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient restClient;

    public OllamaClient(RestClient restClient) {
        this.restClient = restClient;
    }
}
