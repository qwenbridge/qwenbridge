package io.qwenbridge.core.context;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stable identifiers that let one request be traced across REST, the pipeline, asynchronous work
 * and SSE events (QB-006).
 *
 * <ul>
 *   <li>{@code requestId} identifies this single request.
 *   <li>{@code correlationId} groups related requests (e.g. a conversation or a client workflow);
 *       it defaults to the request ID.
 * </ul>
 *
 * Both are always valid per {@link RequestIds}. Invalid client values are replaced, never echoed.
 */
public record CorrelationContext(
    String requestId, String correlationId, boolean requestIdGenerated, boolean requestIdReplaced) {

  public CorrelationContext {
    if (!RequestIds.isValid(requestId)) {
      throw new IllegalArgumentException("requestId is not a valid identifier");
    }
    if (!RequestIds.isValid(correlationId)) {
      throw new IllegalArgumentException("correlationId is not a valid identifier");
    }
  }

  /**
   * Resolves identifiers from untrusted header values.
   *
   * @param requestIdHeader client request ID, may be null, blank or invalid
   * @param correlationIdHeader client correlation ID, may be null, blank or invalid
   * @param generator produces a fresh valid identifier (e.g. a random UUID)
   */
  public static CorrelationContext resolve(
      String requestIdHeader, String correlationIdHeader, Supplier<String> generator) {
    Objects.requireNonNull(generator, "generator must not be null");
    String candidate = requestIdHeader == null ? "" : requestIdHeader.trim();
    boolean supplied = !candidate.isEmpty();
    boolean valid = supplied && RequestIds.isValid(candidate);
    String requestId = valid ? candidate : generator.get();

    String correlation = correlationIdHeader == null ? "" : correlationIdHeader.trim();
    String correlationId = RequestIds.isValid(correlation) ? correlation : requestId;

    return new CorrelationContext(requestId, correlationId, !valid, supplied && !valid);
  }
}
