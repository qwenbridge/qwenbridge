package io.qwenbridge.pipeline.result;

import io.qwenbridge.input.model.MultilingualInput;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RequestContext(String requestId, Instant startedAt, MultilingualInput input) {

  public RequestContext {
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(input, "input must not be null");
  }

  public static RequestContext of(String originalQuery) {
    return of(null, MultilingualInput.of(originalQuery));
  }

  public static RequestContext of(String requestId, String originalQuery) {
    return of(requestId, MultilingualInput.of(originalQuery));
  }

  public static RequestContext of(String requestId, MultilingualInput input) {
    return new RequestContext(
        normalizeRequestId(requestId),
        Instant.now(),
        Objects.requireNonNull(input, "input must not be null"));
  }

  public String originalQuery() {
    return input.originalText();
  }

  private static String normalizeRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return UUID.randomUUID().toString();
    }

    return requestId;
  }
}
