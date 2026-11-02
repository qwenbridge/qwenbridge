package io.qwenbridge.core.queryir;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;
import java.util.Optional;

/**
 * A subjective or fuzzy preference such as "comfortable", "lightweight" or "suspicious" (QB-034).
 * It influences retrieval and ranking but is never an executable filter by itself; turning it into
 * a structured constraint requires an approved mapping (QB-053).
 *
 * @param concept the preference as understood, e.g. {@code lightweight}
 * @param target what it applies to, e.g. an entity type, when known
 */
public record SemanticSignal(
    String id,
    String concept,
    Polarity polarity,
    Optional<String> target,
    Optional<Span> span,
    Confidence confidence) {

  public enum Polarity {
    POSITIVE,
    NEGATIVE
  }

  public SemanticSignal {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(concept, "concept must not be null");
    Objects.requireNonNull(polarity, "polarity must not be null");
    target = target == null ? Optional.empty() : target;
    span = span == null ? Optional.empty() : span;
    Objects.requireNonNull(confidence, "confidence must not be null");
  }
}
