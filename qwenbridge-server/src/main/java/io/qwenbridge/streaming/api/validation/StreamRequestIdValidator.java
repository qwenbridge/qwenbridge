package io.qwenbridge.streaming.api.validation;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StreamRequestIdValidator {

  private static final int MAX_LENGTH = 128;

  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]*$");

  public void validate(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }

    if (requestId.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("requestId must not exceed " + MAX_LENGTH + " characters");
    }

    if (!SAFE_REQUEST_ID.matcher(requestId).matches()) {
      throw new IllegalArgumentException("requestId contains unsupported characters");
    }
  }
}
