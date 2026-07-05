package io.qwenbridge.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwenbridge.ai")
public record AIProperties(String provider) {}
