package io.qwenbridge.intent;

import java.util.Objects;

public record IntentAnalysis(IntentType type, String reason, double confidence) {

  public IntentAnalysis {
    type = Objects.requireNonNullElse(type, IntentType.UNKNOWN);

    if (reason == null || reason.isBlank()) {
      reason = "No intent reason provided.";
    }

    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
    }
  }

  public static IntentAnalysis unknown() {
    return new IntentAnalysis(IntentType.UNKNOWN, "Intent could not be determined.", 0.0);
  }

  public static IntentAnalysis productSearch() {
    return new IntentAnalysis(IntentType.PRODUCT_SEARCH, "Default product search intent.", 0.5);
  }
}
