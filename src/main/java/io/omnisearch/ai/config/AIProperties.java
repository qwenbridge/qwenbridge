package io.omnisearch.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "omnisearch.ai")
public record AIProperties(

        String provider,

        URI baseUrl,

        String chatModel,

        String embeddingModel

) {
}
