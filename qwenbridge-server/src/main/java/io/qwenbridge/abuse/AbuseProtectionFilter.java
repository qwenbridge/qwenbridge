package io.qwenbridge.abuse;

import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.exception.ApiError;
import io.qwenbridge.exception.ErrorCode;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.operations.tracing.TraceContextFilter;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Provider
@Priority(5)
public class AbuseProtectionFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String API_KEY_HEADER = "X-API-Key";
  public static final String RATE_LIMIT_POLICY_HEADER = "X-RateLimit-Policy";

  private static final String DECISION_PROPERTY = "qwenbridge.rateLimitDecision";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AbuseProtectionProperties properties;
  private final RateLimiter rateLimiter;
  private final StreamingSessionRegistry streamingSessionRegistry;
  private final OperationsMetrics metrics;

  @Context RoutingContext routingContext;

  @org.eclipse.microprofile.config.inject.ConfigProperty(
      name = "qwenbridge.security.trusted-proxy.forwarded-headers-enabled",
      defaultValue = "false")
  boolean trustedForwardedHeaders;

  public AbuseProtectionFilter(
      AbuseProtectionProperties properties,
      RateLimiter rateLimiter,
      StreamingSessionRegistry streamingSessionRegistry,
      OperationsMetrics metrics) {
    this.properties = properties;
    this.rateLimiter = rateLimiter;
    this.streamingSessionRegistry = streamingSessionRegistry;
    this.metrics = metrics;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!properties.enabled() || !path(requestContext).startsWith("/api/")) {
      return;
    }

    RateLimitDecision decision = evaluate(requestContext);
    requestContext.setProperty(DECISION_PROPERTY, decision);

    metrics.incrementRateLimit(decision.policy(), decision.allowed() ? "allowed" : "rejected");

    if (routingContext != null) {
      routingContext.response().putHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
      routingContext
          .response()
          .putHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
      routingContext
          .response()
          .putHeader("X-RateLimit-Reset", String.valueOf(decision.resetAt().getEpochSecond()));
      routingContext.response().putHeader(RATE_LIMIT_POLICY_HEADER, decision.policy());
    }

    if (!decision.allowed()) {
      requestContext.abortWith(rateLimitedResponse(requestContext, decision));
    }
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object stored = requestContext.getProperty(DECISION_PROPERTY);
    if (stored instanceof RateLimitDecision decision) {
      applyHeaders(responseContext, decision);
    }
  }

  private RateLimitDecision evaluate(ContainerRequestContext request) {
    if (isStreamRequest(request)
        && streamingSessionRegistry.size() >= properties.concurrentStreamLimit()) {
      return RateLimitDecision.rejected(
          "concurrent-stream",
          properties.concurrentStreamLimit(),
          Instant.now().plus(properties.window()));
    }

    String apiKey = request.getHeaderString(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      RateLimitDecision apiKeyDecision =
          rateLimiter.consume("api-key", fingerprint(apiKey), properties.perApiKeyLimit(), 1);
      if (!apiKeyDecision.allowed()) {
        return apiKeyDecision;
      }
    }

    RateLimitDecision ipDecision =
        rateLimiter.consume("ip", clientIp(request), properties.perIpLimit(), 1);
    if (!ipDecision.allowed()) {
      return ipDecision;
    }

    if (isAiRequest(request)) {
      return rateLimiter.consume(
          "ai-request",
          apiKey != null && !apiKey.isBlank() ? fingerprint(apiKey) : clientIp(request),
          properties.aiRequestQuota(),
          1);
    }

    return ipDecision;
  }

  private String path(ContainerRequestContext request) {
    String path = request.getUriInfo().getPath();
    return path.startsWith("/") ? path : "/" + path;
  }

  private boolean isAiRequest(ContainerRequestContext request) {
    String uri = path(request);
    return uri.contains("/search/analyze")
        || uri.startsWith("/api/v2/search")
        || uri.startsWith("/api/v2/sessions/")
            && (uri.endsWith("/turns") || uri.endsWith("/turns/stream") || uri.endsWith("/accept"))
        || uri.startsWith("/api/v2/domains/") && uri.endsWith("/search")
        || uri.startsWith("/api/v2/relaxations/") && uri.endsWith("/accept")
        || uri.contains("/ai/")
        || uri.contains("/search/stream/");
  }

  private boolean isStreamRequest(ContainerRequestContext request) {
    return path(request).contains("/stream/") || path(request).endsWith("/stream");
  }

  private String clientIp(ContainerRequestContext request) {
    String forwarded = request.getHeaderString("X-Forwarded-For");
    if (trustedForwardedHeaders && forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    if (routingContext != null
        && routingContext.request() != null
        && routingContext.request().remoteAddress() != null) {
      return routingContext.request().remoteAddress().hostAddress();
    }
    return "unknown";
  }

  /**
   * A collision-resistant bucket key for an API key. {@code String.hashCode()} was used before; its
   * collisions can be crafted, letting one client exhaust another's quota (audit S5). The raw key
   * is never stored or logged.
   */
  static String fingerprint(String value) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(value.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private void applyHeaders(ContainerResponseContext response, RateLimitDecision decision) {
    response
        .getHeaders()
        .putSingle(
            HttpHeaders.RETRY_AFTER,
            String.valueOf(
                Math.max(1, decision.resetAt().getEpochSecond() - Instant.now().getEpochSecond())));
    response.getHeaders().putSingle("X-RateLimit-Limit", String.valueOf(decision.limit()));
    response.getHeaders().putSingle("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
    response
        .getHeaders()
        .putSingle("X-RateLimit-Reset", String.valueOf(decision.resetAt().getEpochSecond()));
    response.getHeaders().putSingle(RATE_LIMIT_POLICY_HEADER, decision.policy());
  }

  private Response rateLimitedResponse(
      ContainerRequestContext request, RateLimitDecision decision) {
    String requestId = resolveRequestId(request);
    String traceId = resolveTraceId(request);
    String traceparent = resolveTraceparent(request, traceId);

    ApiError body =
        ApiError.builder()
            .timestamp(Instant.now())
            .status(Response.Status.TOO_MANY_REQUESTS.getStatusCode())
            .error(Response.Status.TOO_MANY_REQUESTS.getReasonPhrase())
            .code(ErrorCode.RATE_LIMITED.name())
            .message("Request rejected by QwenBridge abuse protection policy: " + decision.policy())
            .path(path(request))
            .requestId(requestId)
            .build();

    Response.ResponseBuilder builder =
        Response.status(Response.Status.TOO_MANY_REQUESTS)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .header(ApiHeaders.REQUEST_ID, requestId)
            .header(ApiHeaders.QWENBRIDGE_VERSION, "0.1.0-SNAPSHOT")
            .header(TraceContextFilter.TRACE_ID_HEADER, traceId)
            .header(TraceContextFilter.TRACEPARENT_HEADER, traceparent);

    return builder.build();
  }

  private String resolveRequestId(ContainerRequestContext request) {
    Object attribute = request.getProperty(ApiHeaders.REQUEST_ID);
    if (attribute instanceof String value && !value.isBlank()) {
      return value.trim();
    }

    String headerValue = request.getHeaderString(ApiHeaders.REQUEST_ID);
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue.trim();
    }

    return UUID.randomUUID().toString();
  }

  private String resolveTraceId(ContainerRequestContext request) {
    String traceparent = request.getHeaderString(TraceContextFilter.TRACEPARENT_HEADER);
    if (traceparent != null
        && traceparent.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
      return traceparent.substring(3, 35);
    }

    return randomHex(16);
  }

  private String resolveTraceparent(ContainerRequestContext request, String traceId) {
    String traceparent = request.getHeaderString(TraceContextFilter.TRACEPARENT_HEADER);
    if (traceparent != null
        && traceparent.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
      return traceparent;
    }

    return "00-" + traceId + "-" + randomHex(8) + "-01";
  }

  private String randomHex(int bytes) {
    byte[] value = new byte[bytes];
    RANDOM.nextBytes(value);
    return HexFormat.of().formatHex(value);
  }
}
