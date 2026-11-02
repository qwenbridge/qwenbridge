package io.qwenbridge.core.request;

/**
 * Structural limits checked before any expensive AI or retrieval work (QB-005). Text lengths are
 * counted in Unicode code points, not UTF-16 units or bytes, so emoji, supplementary-plane and
 * non-Latin scripts are not penalised relative to ASCII.
 */
public record RequestLimits(
    int maxQueryCodePoints,
    int maxContextEntries,
    int maxContextKeyLength,
    int maxContextValueCodePoints,
    int maxDomainHints,
    int maxDomainHintLength,
    int maxJsonNestingDepth) {

  public static final RequestLimits DEFAULTS = new RequestLimits(2048, 16, 64, 256, 8, 64, 64);

  public RequestLimits {
    requirePositive(maxQueryCodePoints, "maxQueryCodePoints");
    requirePositive(maxContextEntries, "maxContextEntries");
    requirePositive(maxContextKeyLength, "maxContextKeyLength");
    requirePositive(maxContextValueCodePoints, "maxContextValueCodePoints");
    requirePositive(maxDomainHints, "maxDomainHints");
    requirePositive(maxDomainHintLength, "maxDomainHintLength");
    requirePositive(maxJsonNestingDepth, "maxJsonNestingDepth");
  }

  private static void requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
