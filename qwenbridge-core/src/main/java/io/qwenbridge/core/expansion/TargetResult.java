package io.qwenbridge.core.expansion;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.PreservationReport;
import java.util.Objects;
import java.util.Optional;

/**
 * The result for one configured target language (QB-286). A text is present only for {@code
 * SUCCESS}, {@code UNCHANGED} and {@code LOW_CONFIDENCE}; nothing is ever invented to fill a failed
 * target.
 *
 * @param requested the identifier as configured
 * @param language the normalized target, when the identifier was valid
 * @param sourceLanguage the language translated from ({@code mul} for mixed text), when known
 * @param originalRef reference to the original query ({@link ExpansionResult#originalRef()})
 * @param provider the provider that produced the text or last failed
 * @param reason why the target failed or is unsupported; also set for {@code LOW_CONFIDENCE}
 * @param preservation the meaning-preservation check of the translation, when one was produced
 * @param identifiersPreserved whether every protected identifier survived verbatim (true when there
 *     is no text to check)
 * @param retrieval whether the variant is used for retrieval under the policy
 */
public record TargetResult(
    String requested,
    Optional<LanguageTag> language,
    Optional<LanguageTag> sourceLanguage,
    String originalRef,
    TargetStatus status,
    Optional<String> text,
    Optional<String> provider,
    Confidence confidence,
    Optional<String> reason,
    Optional<PreservationReport> preservation,
    boolean identifiersPreserved,
    boolean retrieval,
    long elapsedMillis) {

  public TargetResult {
    Objects.requireNonNull(requested, "requested must not be null");
    language = language == null ? Optional.empty() : language;
    sourceLanguage = sourceLanguage == null ? Optional.empty() : sourceLanguage;
    Objects.requireNonNull(originalRef, "originalRef must not be null");
    Objects.requireNonNull(status, "status must not be null");
    text = text == null ? Optional.empty() : text;
    provider = provider == null ? Optional.empty() : provider;
    Objects.requireNonNull(confidence, "confidence must not be null");
    reason = reason == null ? Optional.empty() : reason;
    preservation = preservation == null ? Optional.empty() : preservation;
    boolean hasText =
        status == TargetStatus.SUCCESS
            || status == TargetStatus.UNCHANGED
            || status == TargetStatus.LOW_CONFIDENCE;
    if (hasText != text.isPresent())
      throw new IllegalArgumentException("a text is present exactly for produced variants");
  }

  TargetResult withRetrieval(boolean used) {
    return new TargetResult(
        requested,
        language,
        sourceLanguage,
        originalRef,
        status,
        text,
        provider,
        confidence,
        reason,
        preservation,
        identifiersPreserved,
        used,
        elapsedMillis);
  }
}
