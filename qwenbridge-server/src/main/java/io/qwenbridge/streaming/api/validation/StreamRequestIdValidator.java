package io.qwenbridge.streaming.api.validation;

import io.qwenbridge.core.context.RequestIds;
import jakarta.enterprise.context.ApplicationScoped;

/** SSE path request IDs follow the same shared identifier rule as REST headers (QB-006). */
@ApplicationScoped
public class StreamRequestIdValidator {

  public void validate(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }

    if (requestId.length() > RequestIds.MAX_LENGTH) {
      throw new IllegalArgumentException(
          "requestId must not exceed " + RequestIds.MAX_LENGTH + " characters");
    }

    if (!RequestIds.isValid(requestId)) {
      throw new IllegalArgumentException("requestId contains unsupported characters");
    }
  }
}
