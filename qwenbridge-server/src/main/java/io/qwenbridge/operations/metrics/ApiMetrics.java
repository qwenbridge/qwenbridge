package io.qwenbridge.operations.metrics;

import io.micrometer.core.instrument.Metrics;
import io.qwenbridge.exception.ErrorCode;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reliability and stage metrics (QB-211, QB-213), recorded on the global Micrometer registry so
 * that static error mappers can report too.
 *
 * <ul>
 *   <li>{@code qwenbridge.api.errors{api,code,category,class}}: every error response or failed
 *       stream. {@code class} is {@code expected} for rejections that are the system working as
 *       intended (validation, authentication, authorization, not found, conflict, rate limit,
 *       unsupported, cancelled) and {@code operational} for timeouts, dependency failures and
 *       internal errors. Operational error rate = operational errors / all requests.
 *   <li>{@code qwenbridge.api.interruptions{reason}}: requests stopped by cancellation or deadline.
 *   <li>{@code qwenbridge.search.stage{stage,contract}}: duration of each recorded search stage.
 *   <li>{@code qwenbridge.search.duration{contract,status}}: whole search, server side.
 * </ul>
 */
public final class ApiMetrics {

  private ApiMetrics() {}

  public static String errorClass(ErrorCode code) {
    return switch (code.category()) {
      case TIMEOUT, DEPENDENCY, INTERNAL -> "operational";
      default -> "expected";
    };
  }

  public static void error(String api, ErrorCode code) {
    Metrics.counter(
            "qwenbridge.api.errors",
            "api",
            api,
            "code",
            code.name(),
            "category",
            code.category().name(),
            "class",
            errorClass(code))
        .increment();
  }

  public static void interruption(String reason) {
    Metrics.counter("qwenbridge.api.interruptions", "reason", reason).increment();
  }

  public static void search(
      String contract, String status, long totalMs, Map<String, Long> stages) {
    String c = contract == null ? "v2" : contract;
    Metrics.timer("qwenbridge.search.duration", "contract", c, "status", status)
        .record(totalMs, TimeUnit.MILLISECONDS);
    if (stages != null)
      stages.forEach(
          (stage, ms) -> {
            if (!"total".equals(stage) && ms != null && ms >= 0)
              Metrics.timer("qwenbridge.search.stage", "stage", stage, "contract", c)
                  .record(ms, TimeUnit.MILLISECONDS);
          });
  }
}
