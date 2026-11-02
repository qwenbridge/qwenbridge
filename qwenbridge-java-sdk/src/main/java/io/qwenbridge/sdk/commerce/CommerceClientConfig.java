package io.qwenbridge.sdk.commerce;

import io.qwenbridge.sdk.retry.RetryPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of a {@link CommerceClient} (QB-186, QB-190).
 *
 * @param requestTimeout per call; also sent as {@code X-Request-Timeout-Ms} so the server stops its
 *     own work at the same deadline
 * @param retryPolicy applied only to safe operations (search, refine, reads); never to session
 *     turns, acceptances, streams or cancellations
 */
public record CommerceClientConfig(
    URI baseUrl,
    String bearerToken,
    Duration connectTimeout,
    Duration requestTimeout,
    RetryPolicy retryPolicy) {

  public CommerceClientConfig {
    Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
    Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    bearerToken = bearerToken == null ? "" : bearerToken;
    if (connectTimeout.isNegative() || connectTimeout.isZero())
      throw new IllegalArgumentException("connectTimeout must be positive");
    if (requestTimeout.isNegative() || requestTimeout.isZero())
      throw new IllegalArgumentException("requestTimeout must be positive");
  }

  /** 5 s connect, 120 s per call, up to 3 attempts for safe operations. */
  public static CommerceClientConfig of(URI baseUrl, String bearerToken) {
    return new CommerceClientConfig(
        baseUrl,
        bearerToken,
        Duration.ofSeconds(5),
        Duration.ofSeconds(120),
        RetryPolicy.defaultPolicy());
  }

  @Override
  public String toString() {
    // Never print the credential.
    return "CommerceClientConfig[baseUrl="
        + baseUrl
        + ", bearerToken="
        + (bearerToken.isEmpty() ? "" : "***")
        + ", connectTimeout="
        + connectTimeout
        + ", requestTimeout="
        + requestTimeout
        + ", retryPolicy="
        + retryPolicy
        + "]";
  }
}
