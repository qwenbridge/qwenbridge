package io.qwenbridge.core.ai;

/**
 * What a chat provider supports (QB-243, QB-246), checked before a call is made.
 *
 * @param maxInputChars largest prompt accepted
 * @param maxOutputTokens largest output the provider can be asked for
 */
public record ChatCapabilities(
    boolean streaming,
    boolean structuredOutput,
    boolean cancellation,
    int maxInputChars,
    int maxOutputTokens) {
  public ChatCapabilities {
    if (maxInputChars < 1 || maxOutputTokens < 1)
      throw new IllegalArgumentException("limits must be positive");
  }
}
