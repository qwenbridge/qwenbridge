package io.qwenbridge.pipeline.result;

import java.util.List;

public record PolicyResult(boolean passed, List<String> violations) {
  public PolicyResult {
    violations = violations == null ? List.of() : List.copyOf(violations);
  }

  public static PolicyResult allow() {
    return new PolicyResult(true, List.of());
  }
}
