package io.omnisearch.ai.provider.ollama.client;

import io.omnisearch.ai.provider.ollama.config.OllamaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaClientConfig {

    @Bean
    RestClient ollamaRestClient(
            RestClient.Builder builder,
            OllamaProperties properties
    ) {

        return builder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }
}
