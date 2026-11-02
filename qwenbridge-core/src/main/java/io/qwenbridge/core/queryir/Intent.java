package io.qwenbridge.core.queryir;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;
import java.util.Optional;

/**
 * A recognized user goal (QB-022, QB-023). {@code type} is a taxonomy identifier: universal intents
 * are plain ({@code search}, {@code compare}, {@code investigate}); domain packs extend with a
 * namespace ({@code commerce.reorder}). The QueryIR does not fix the taxonomy; Phase 4 does.
 */
public record Intent(String id, String type, Confidence confidence, Optional<Span> span) {

  public Intent {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    span = span == null ? Optional.empty() : span;
  }
}
