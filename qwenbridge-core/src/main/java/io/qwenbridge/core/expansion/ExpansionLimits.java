package io.qwenbridge.core.expansion;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounds of one expansion (QB-286).
 *
 * @param perLanguage time budget of one target language (one provider call; a fallback provider
 *     gets what is left)
 * @param overall time budget of the whole expansion; unfinished targets are reported {@code FAILED}
 *     with reason {@code TIMEOUT}
 * @param maxParallel targets translated at the same time within one request
 */
public record ExpansionLimits(Duration perLanguage, Duration overall, int maxParallel) {

  public static final ExpansionLimits DEFAULT =
      new ExpansionLimits(Duration.ofSeconds(5), Duration.ofSeconds(15), 4);

  public ExpansionLimits {
    Objects.requireNonNull(perLanguage, "perLanguage must not be null");
    Objects.requireNonNull(overall, "overall must not be null");
    if (perLanguage.isNegative()
        || perLanguage.isZero()
        || overall.isNegative()
        || overall.isZero()) throw new IllegalArgumentException("timeouts must be positive");
    if (maxParallel < 1 || maxParallel > 64)
      throw new IllegalArgumentException("maxParallel must be within 1..64");
  }
}
