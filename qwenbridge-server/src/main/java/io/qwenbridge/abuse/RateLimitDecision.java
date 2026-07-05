package io.qwenbridge.abuse;

import java.time.Instant;

public record RateLimitDecision(
    boolean allowed, String policy, long limit, long remaining, Instant resetAt) {
  public static RateLimitDecision allowed(
      String policy, long limit, long remaining, Instant resetAt) {
    return new RateLimitDecision(true, policy, limit, Math.max(0, remaining), resetAt);
  }

  public static RateLimitDecision rejected(String policy, long limit, Instant resetAt) {
    return new RateLimitDecision(false, policy, limit, 0, resetAt);
  }
}
