package io.qwenbridge.operations.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class OperationsMetrics {

  public static final String PREFIX = "qwenbridge";

  private final MeterRegistry registry;
  private final AtomicInteger activeSseSessions = new AtomicInteger();

  public OperationsMetrics(MeterRegistry registry) {
    this.registry = registry;
    registry.gauge(PREFIX + ".sse.sessions.active", activeSseSessions);
  }

  public void recordHttpRequest(String method, String path, int status, Duration duration) {
    Timer.builder(PREFIX + ".http.server.requests")
        .tag("method", method)
        .tag("path", path)
        .tag("status", String.valueOf(status))
        .register(registry)
        .record(duration);
  }

  public void recordAiProvider(
      String provider, String operation, String outcome, Duration duration) {
    Timer.builder(PREFIX + ".ai.provider.requests")
        .tag("provider", provider)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .register(registry)
        .record(duration);
  }

  public void recordOpenSearch(String operation, String outcome, Duration duration) {
    Timer.builder(PREFIX + ".opensearch.requests")
        .tag("operation", operation)
        .tag("outcome", outcome)
        .register(registry)
        .record(duration);
  }

  public void incrementCache(String cache, String result) {
    Counter.builder(PREFIX + ".redis.cache.events")
        .tag("cache", cache)
        .tag("result", result)
        .register(registry)
        .increment();
  }

  public void incrementRateLimit(String policy, String decision) {
    Counter.builder(PREFIX + ".ratelimit.decisions")
        .tag("policy", policy)
        .tag("decision", decision)
        .register(registry)
        .increment();
  }

  public void sessionOpened() {
    activeSseSessions.incrementAndGet();
    Counter.builder(PREFIX + ".sse.sessions.opened").register(registry).increment();
  }

  public void sessionClosed(String reason) {
    activeSseSessions.updateAndGet(value -> Math.max(0, value - 1));
    Counter.builder(PREFIX + ".sse.sessions.closed")
        .tag("reason", reason)
        .register(registry)
        .increment();
  }

  public void recordSseEvent(String eventName) {
    Counter.builder(PREFIX + ".sse.events")
        .tag("event", eventName == null ? "unknown" : eventName)
        .register(registry)
        .increment();
  }
}
