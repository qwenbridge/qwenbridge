package io.qwenbridge.sdk.config;

import io.qwenbridge.sdk.retry.RetryPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record QwenBridgeClientConfig(
    URI baseUrl, Duration connectTimeout, Duration requestTimeout, RetryPolicy retryPolicy) {

  public QwenBridgeClientConfig {
    Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
    Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
  }

  public QwenBridgeClientConfig(URI baseUrl, Duration connectTimeout, Duration requestTimeout) {
    this(baseUrl, connectTimeout, requestTimeout, RetryPolicy.disabled());
  }

  public static QwenBridgeClientConfig localDefault() {
    return new QwenBridgeClientConfig(
        URI.create("http://localhost:8080"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(30),
        RetryPolicy.defaultPolicy());
  }
}
