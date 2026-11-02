package io.qwenbridge.core.queryir;

import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Multilingual expansion of the query (QueryIR 1.1, QB-286). The query's {@code originalText} is
 * unchanged and its constraints stay grounded in it; this records the text that was expanded and
 * one entry per configured target language, including failed and unsupported ones.
 *
 * @param status overall status, e.g. {@code COMPLETE}, {@code PARTIAL}, {@code NOT_CONFIGURED}
 * @param policy the expansion policy in effect
 * @param sourceText the expanded text (normalized, typo-corrected when applied)
 * @param sourceLanguage the reliably detected source language, if any ({@code mul} when mixed)
 * @param configuredTargets the configured identifiers, as written
 */
public record QueryExpansion(
    String status,
    String policy,
    String sourceText,
    Optional<LanguageTag> sourceLanguage,
    List<String> configuredTargets,
    List<Target> targets) {

  /**
   * One configured target language.
   *
   * @param requested the identifier as configured
   * @param language the normalized BCP 47 target, when valid
   * @param text the variant; present only when one was produced
   * @param confidence the confidence kind ({@code DETERMINISTIC}, {@code HEURISTIC}, …)
   * @param preservation the meaning-preservation status of a translation
   * @param retrieval whether retrieval uses this variant
   */
  public record Target(
      String requested,
      Optional<LanguageTag> language,
      String status,
      Optional<String> text,
      Optional<String> provider,
      String confidence,
      Optional<String> reason,
      Optional<String> preservation,
      boolean retrieval) {
    public Target {
      Objects.requireNonNull(requested, "requested must not be null");
      language = language == null ? Optional.empty() : language;
      Objects.requireNonNull(status, "status must not be null");
      text = text == null ? Optional.empty() : text;
      provider = provider == null ? Optional.empty() : provider;
      Objects.requireNonNull(confidence, "confidence must not be null");
      reason = reason == null ? Optional.empty() : reason;
      preservation = preservation == null ? Optional.empty() : preservation;
      if (retrieval && text.isEmpty())
        throw new IllegalArgumentException("a retrieval variant needs a text");
    }
  }

  public QueryExpansion {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(sourceText, "sourceText must not be null");
    sourceLanguage = sourceLanguage == null ? Optional.empty() : sourceLanguage;
    configuredTargets = List.copyOf(configuredTargets);
    targets = List.copyOf(targets);
  }
}
