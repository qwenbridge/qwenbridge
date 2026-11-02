package io.qwenbridge.core.expansion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The effective expansion settings of an application or tenant (QB-286). Nothing is enabled and no
 * target language exists by default.
 *
 * @param targetLanguages configured identifiers, in order, exactly as written
 * @param aliases explicit compatibility aliases (for example {@code SE} to {@code sv}); matched
 *     exactly
 * @param minConfidence a translation whose reported score or coverage is below this is {@code
 *     LOW_CONFIDENCE}
 */
public record ExpansionSettings(
    boolean enabled,
    List<String> targetLanguages,
    ExpansionPolicy policy,
    Map<String, String> aliases,
    double minConfidence) {

  public static final double DEFAULT_MIN_CONFIDENCE = 0.5;

  public ExpansionSettings {
    targetLanguages = targetLanguages == null ? List.of() : List.copyOf(targetLanguages);
    Objects.requireNonNull(policy, "policy must not be null");
    aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
    if (Double.isNaN(minConfidence) || minConfidence < 0 || minConfidence > 1)
      throw new IllegalArgumentException("minConfidence must be within 0..1");
  }

  public static ExpansionSettings disabled() {
    return new ExpansionSettings(
        false, List.of(), ExpansionPolicy.ALL_TARGETS, Map.of(), DEFAULT_MIN_CONFIDENCE);
  }

  public List<TargetLanguage> resolved() {
    return TargetLanguages.resolve(targetLanguages, aliases);
  }
}
