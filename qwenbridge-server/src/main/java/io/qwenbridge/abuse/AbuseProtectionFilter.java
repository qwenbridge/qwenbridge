package io.qwenbridge.abuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.exception.ApiError;
import io.qwenbridge.exception.ErrorCode;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.operations.tracing.TraceContextFilter;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AbuseProtectionProperties.class)
@ConditionalOnProperty(
    prefix = "qwenbridge.abuse",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AbuseProtectionFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-API-Key";
  public static final String RATE_LIMIT_POLICY_HEADER = "X-RateLimit-Policy";

  private static final SecureRandom RANDOM = new SecureRandom();

  private final AbuseProtectionProperties properties;
  private final RateLimiter rateLimiter;
  private final StreamingSessionRegistry streamingSessionRegistry;
  private final ObjectMapper objectMapper;
  private final OperationsMetrics metrics;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    RateLimitDecision decision = evaluate(request);
    applyHeaders(response, decision);

    metrics.incrementRateLimit(decision.policy(), decision.allowed() ? "allowed" : "rejected");

    if (!decision.allowed()) {
      writeRateLimitedResponse(request, response, decision);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private RateLimitDecision evaluate(HttpServletRequest request) {
    if (request.getContentLengthLong() > properties.requestSizeLimitBytes()) {
      return RateLimitDecision.rejected(
          "request-size",
          properties.requestSizeLimitBytes(),
          Instant.now().plus(properties.window()));
    }

    if (isStreamRequest(request)
        && streamingSessionRegistry.size() >= properties.concurrentStreamLimit()) {
      return RateLimitDecision.rejected(
          "concurrent-stream",
          properties.concurrentStreamLimit(),
          Instant.now().plus(properties.window()));
    }

    String apiKey = request.getHeader(API_KEY_HEADER);
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

  private boolean isAiRequest(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.contains("/search/analyze")
        || uri.contains("/ai/")
        || uri.contains("/search/stream/");
  }

  private boolean isStreamRequest(HttpServletRequest request) {
    return request.getRequestURI().contains("/stream/");
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String fingerprint(String value) {
    return Integer.toHexString(value.trim().hashCode());
  }

  private void applyHeaders(HttpServletResponse response, RateLimitDecision decision) {
    response.setHeader(
        HttpHeaders.RETRY_AFTER,
        String.valueOf(
            Math.max(1, decision.resetAt().getEpochSecond() - Instant.now().getEpochSecond())));
    response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
    response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetAt().getEpochSecond()));
    response.setHeader(RATE_LIMIT_POLICY_HEADER, decision.policy());
  }

  private void writeRateLimitedResponse(
      HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision)
      throws IOException {
    String requestId = resolveRequestId(request);
    String traceId = resolveTraceId(request);
    String traceparent = resolveTraceparent(request, traceId);

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    response.setHeader(ApiHeaders.REQUEST_ID, requestId);
    response.setHeader(ApiHeaders.QWENBRIDGE_VERSION, "0.1.0-SNAPSHOT");
    response.setHeader(TraceContextFilter.TRACE_ID_HEADER, traceId);
    response.setHeader(TraceContextFilter.TRACEPARENT_HEADER, traceparent);

    ApiError body =
        ApiError.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
            .code(ErrorCode.RATE_LIMITED.name())
            .message("Request rejected by QwenBridge abuse protection policy: " + decision.policy())
            .path(request.getRequestURI())
            .requestId(requestId)
            .build();

    objectMapper.writeValue(response.getWriter(), body);
  }

  private String resolveRequestId(HttpServletRequest request) {
    Object attribute = request.getAttribute(ApiHeaders.REQUEST_ID);
    if (attribute instanceof String value && !value.isBlank()) {
      return value.trim();
    }

    String headerValue = request.getHeader(ApiHeaders.REQUEST_ID);
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue.trim();
    }

    return UUID.randomUUID().toString();
  }

  private String resolveTraceId(HttpServletRequest request) {
    String traceparent = request.getHeader(TraceContextFilter.TRACEPARENT_HEADER);
    if (traceparent != null
        && traceparent.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
      return traceparent.substring(3, 35);
    }

    return randomHex(16);
  }

  private String resolveTraceparent(HttpServletRequest request, String traceId) {
    String traceparent = request.getHeader(TraceContextFilter.TRACEPARENT_HEADER);
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
