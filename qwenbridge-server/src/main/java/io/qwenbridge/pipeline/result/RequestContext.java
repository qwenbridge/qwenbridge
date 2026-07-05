package io.qwenbridge.pipeline.result;

import java.time.Instant;
import java.util.UUID;

public record RequestContext(String requestId, Instant startedAt, String originalQuery) {
  public static RequestContext of(String originalQuery) {
    return of(null, originalQuery);
  }

  public static RequestContext of(String requestId, String originalQuery) {
    return new RequestContext(normalizeRequestId(requestId), Instant.now(), originalQuery);
  }

  private static String normalizeRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return UUID.randomUUID().toString();
    }

    return requestId;
  }
}
