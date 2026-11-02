package io.qwenbridge.core.conversation;

import java.time.Duration;

/**
 * Bounds of a conversation context (QB-143): count limits and a serialized byte limit, so neither a
 * long conversation nor a forged context can grow without bound.
 */
public record ContextLimits(
    int maxTurns,
    int maxConstraints,
    int maxLockedFields,
    int maxSuppressed,
    int maxBytes,
    Duration maxTtl) {

  public static final ContextLimits DEFAULT =
      new ContextLimits(50, 32, 32, 100, 16_384, Duration.ofHours(1));

  public ContextLimits {
    if (maxTurns < 1
        || maxConstraints < 1
        || maxLockedFields < 0
        || maxSuppressed < 0
        || maxBytes < 256
        || maxTtl == null
        || maxTtl.isNegative()
        || maxTtl.isZero()) {
      throw new IllegalArgumentException("invalid context limits");
    }
  }
}
