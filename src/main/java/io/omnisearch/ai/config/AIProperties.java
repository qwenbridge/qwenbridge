package io.omnisearch.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnisearch.ai")
public record AIProperties(

        String provider

) {
}