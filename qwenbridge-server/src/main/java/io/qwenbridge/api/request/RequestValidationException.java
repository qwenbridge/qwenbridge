package io.qwenbridge.api.request;

import io.qwenbridge.core.request.RequestViolation;
import java.util.List;
import java.util.stream.Collectors;

/** Raised when a request violates the universal request contract; mapped to HTTP 400. */
public class RequestValidationException extends RuntimeException {

  private final transient List<RequestViolation> violations;

  public RequestValidationException(List<RequestViolation> violations) {
    super(describe(violations), null, false, false);
    this.violations = violations == null ? List.of() : List.copyOf(violations);
  }

  public List<RequestViolation> violations() {
    return violations;
  }

  private static String describe(List<RequestViolation> violations) {
    if (violations == null || violations.isEmpty()) {
      return "Request is invalid";
    }
    return violations.stream().map(RequestViolation::toString).collect(Collectors.joining(", "));
  }
}
