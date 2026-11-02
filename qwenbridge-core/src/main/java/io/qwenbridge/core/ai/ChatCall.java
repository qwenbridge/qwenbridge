package io.qwenbridge.core.ai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * One chat request (QB-243). The caller has already been authorized; providers only see the prompt.
 *
 * @param outputSchema a JSON schema the output must follow, when structured output is needed
 * @param cancelled polled by the router; providers that support cancellation also poll it
 */
public record ChatCall(
    String prompt,
    Optional<Map<String, Object>> outputSchema,
    boolean streaming,
    int maxOutputTokens,
    Duration timeout,
    BooleanSupplier cancelled) {
  public ChatCall {
    Objects.requireNonNull(prompt, "prompt must not be null");
    outputSchema = outputSchema == null ? Optional.empty() : outputSchema.map(Map::copyOf);
    Objects.requireNonNull(timeout, "timeout must not be null");
    if (maxOutputTokens < 1) throw new IllegalArgumentException("maxOutputTokens must be positive");
    cancelled = cancelled == null ? () -> false : cancelled;
  }

  public static ChatCall of(String prompt, Duration timeout) {
    return new ChatCall(prompt, Optional.empty(), false, 1024, timeout, () -> false);
  }
}
