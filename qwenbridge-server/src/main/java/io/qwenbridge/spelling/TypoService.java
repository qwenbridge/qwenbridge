package io.qwenbridge.spelling;

import io.qwenbridge.core.queryir.TextCorrection;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.spelling.CorrectionResult;
import io.qwenbridge.core.spelling.TypoCorrector;
import io.qwenbridge.language.LanguageAnalysis;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.Optional;

/** Runs the typo engine with the caller's tenant settings (QB-291). */
@Singleton
public final class TypoService {

  private final TypoConfiguration configuration;

  public TypoService(TypoConfiguration configuration) {
    this.configuration = configuration;
  }

  public CorrectionResult correct(
      String tenant, String original, String normalized, LanguageAnalysis language) {
    return TypoCorrector.correct(
        original,
        normalized,
        configuration.settings(tenant),
        detection(language),
        configuration.providers());
  }

  static TypoCorrector.Detection detection(LanguageAnalysis language) {
    if (language == null) return TypoCorrector.Detection.none();
    var detection = language.detection();
    Optional<LanguageTag> tag =
        detection.determined() ? Optional.of(detection.language()) : Optional.empty();
    return new TypoCorrector.Detection(
        tag, detection.determined() && !language.mixed(), language.mixed());
  }

  /**
   * Whether the result belongs in the QueryIR and responses: only once typo correction is enabled.
   */
  public static boolean reportable(CorrectionResult result) {
    return result != null && result.status() != CorrectionResult.Status.DISABLED;
  }

  public static TextCorrection toQueryIr(CorrectionResult r) {
    return new TextCorrection(
        r.status().name(),
        r.mode().name(),
        r.normalized(),
        r.corrected(),
        r.languages().stream().map(l -> l.configured()).toList(),
        r.activeLanguages(),
        r.detectedLanguage(),
        r.corrections().stream()
            .map(
                c ->
                    new TextCorrection.Correction(
                        c.token(),
                        c.start(),
                        c.end(),
                        c.language(),
                        c.replacement(),
                        BigDecimal.valueOf(c.confidence()),
                        c.applied(),
                        c.reason()))
            .toList());
  }
}
