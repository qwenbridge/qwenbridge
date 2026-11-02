package io.qwenbridge.core.request;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The domain-neutral, versioned request contract shared by every transport and the intelligence
 * pipeline (QB-001). It deliberately has no Commerce, DevOps or Security fields: domain-specific
 * meaning is expressed through {@code domainHints} and resolved later by Domain Packs.
 *
 * <p>{@code query} is kept exactly as received — no normalization, trimming or re-encoding happens
 * at this boundary, so the original user text is always recoverable (QB-002, QB-017).
 *
 * <p>Instances are only valid when produced by {@link UniversalRequestParser}; the constructor
 * enforces structural invariants, not the configurable limits.
 */
public record UniversalRequest(
    int contractVersion,
    Optional<String> requestId,
    String query,
    LanguagePreferences language,
    Map<String, String> context,
    List<String> domainHints) {

  /** Current contract version. Additive, backward-compatible changes keep this number. */
  public static final int CONTRACT_VERSION = 1;

  public UniversalRequest {
    if (contractVersion != CONTRACT_VERSION) {
      throw new IllegalArgumentException("Unsupported contract version");
    }
    requestId = requestId == null ? Optional.empty() : requestId;
    Objects.requireNonNull(query, "query must not be null");
    language = language == null ? LanguagePreferences.NONE : language;
    // Validated context has no null keys or values, so an immutable copy is safe.
    context = context == null ? Map.of() : Map.copyOf(context);
    domainHints = domainHints == null ? List.of() : List.copyOf(domainHints);
  }

  public TextDirection direction() {
    return TextDirection.of(query);
  }

  /** Initial language metadata; detection fills in {@code detected} later in the pipeline. */
  public LanguageMetadata initialLanguageMetadata() {
    return LanguageMetadata.undetected(language, query);
  }
}
