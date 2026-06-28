package io.qwenbridge.execution.provider.opensearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "qwenbridge.search.opensearch")
public record OpenSearchProperties(
        String baseUrl,
        String index,
        int defaultSize,
        Duration connectTimeout,
        Duration readTimeout
) {
    public OpenSearchProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:9200";
        }

        if (index == null || index.isBlank()) {
            index = "qwenbridge-products";
        }

        if (defaultSize <= 0) {
            defaultSize = 10;
        }

        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }

        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
    }
}