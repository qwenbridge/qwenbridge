package io.qwenbridge.core.expansion;

import java.util.List;
import java.util.Objects;

/**
 * The multilingual expansion of one query (QB-286): one result per configured, non-duplicate target
 * language, in configured order, and the texts retrieval may use.
 *
 * @param original the query exactly as received; never replaced
 * @param originalRef a stable reference to the original ({@code sha256:} prefix of its UTF-8
 *     bytes), repeated in every target result for correlation
 * @param sourceText the text that was expanded: the normalized query, with typo corrections when
 *     QB-291 applied them
 * @param configured every configured identifier with its resolution, duplicates included
 * @param targets one result per configured identifier except duplicates
 * @param retrievalTexts the source text followed by the distinct variants usable under the policy
 *     (no duplicates, no failed targets)
 * @param explanations how the source language was established when detection was overruled
 */
public record ExpansionResult(
    Status status,
    ExpansionPolicy policy,
    String original,
    String originalRef,
    String sourceText,
    SourceLanguage source,
    List<TargetLanguage> configured,
    List<TargetResult> targets,
    List<String> retrievalTexts,
    List<String> explanations,
    long elapsedMillis) {

  public enum Status {
    /** Expansion is not enabled; nothing was executed. */
    DISABLED,
    /** Enabled, but no target language is configured; nothing is assumed. */
    NOT_CONFIGURED,
    /** Every target is SUCCESS or UNCHANGED. */
    COMPLETE,
    /** Some targets produced a usable variant and some did not. */
    PARTIAL,
    /** No target produced a usable variant, or STRICT_ALL_TARGETS and one target failed. */
    FAILED,
    /** The request was cancelled before every target finished. */
    CANCELLED
  }

  public ExpansionResult {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(original, "original must not be null");
    Objects.requireNonNull(originalRef, "originalRef must not be null");
    Objects.requireNonNull(sourceText, "sourceText must not be null");
    Objects.requireNonNull(source, "source must not be null");
    configured = List.copyOf(configured);
    targets = List.copyOf(targets);
    retrievalTexts = List.copyOf(retrievalTexts);
    explanations = List.copyOf(explanations);
  }

  /** Whether this belongs in QueryIR and responses: only once expansion is enabled. */
  public boolean reportable() {
    return status != Status.DISABLED;
  }
}
