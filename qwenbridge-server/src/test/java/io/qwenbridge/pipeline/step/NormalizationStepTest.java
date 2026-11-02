package io.qwenbridge.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.input.model.InputSource;
import io.qwenbridge.input.model.MultilingualInput;
import io.qwenbridge.language.LanguageService;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.rule.ControlCharacterNormalizer;
import io.qwenbridge.normalization.rule.HtmlEntityNormalizer;
import io.qwenbridge.normalization.rule.UnicodeNormalizer;
import io.qwenbridge.normalization.rule.UrlDecodeNormalizer;
import io.qwenbridge.normalization.rule.WhitespaceNormalizer;
import io.qwenbridge.normalization.service.DefaultInputNormalizer;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QB-009/010: the search form preserves meaning (no decoding, no NFKC) while the threat canonical
 * form keeps the aggressive decoding that exposes obfuscated payloads.
 */
class NormalizationStepTest {

  private final NormalizationStep step =
      new NormalizationStep(
          new DefaultInputNormalizer(
              List.of(
                  new UrlDecodeNormalizer(),
                  new HtmlEntityNormalizer(),
                  new UnicodeNormalizer(),
                  new ControlCharacterNormalizer(),
                  new WhitespaceNormalizer())));

  @Test
  void encodedPayloadIsDecodedForThreatDetectionButNotForSearch() {
    ExecutionContext context = new ExecutionContext("%253Cscript%253E alert(1)");

    NormalizedInput result = step.execute(context);

    assertThat(result.originalQuery()).isEqualTo("%253Cscript%253E alert(1)");
    assertThat(result.normalizedQuery()).isEqualTo("%253Cscript%253E alert(1)");
    assertThat(result.threatCanonicalQuery()).isEqualTo("<script> alert(1)");
  }

  @Test
  void compatibilityFoldingAppliesOnlyToThreatCanonicalForm() {
    String fullWidth = "ＳＥＬＥＣＴ * ＦＲＯＭ users";
    NormalizedInput result = step.execute(new ExecutionContext(fullWidth));
    assertThat(result.normalizedQuery()).isEqualTo(fullWidth);
    assertThat(result.threatCanonicalQuery()).isEqualTo("SELECT * FROM users");
  }

  @Test
  void searchFormIsTidiedAndTraced() {
    NormalizedInput result = step.execute(new ExecutionContext("  black‮ shoes  "));
    assertThat(result.normalizedQuery()).isEqualTo("black shoes");
    assertThat(result.searchSteps()).containsExactly("disallowed-controls", "whitespace");
    assertThat(result.changed()).isTrue();
  }

  @Test
  void persianVariantsFollowTheDetectedLanguage() {
    LanguageStep languageStep = new LanguageStep(new LanguageService());
    String arabicKafYeh = "كفش مشكي پیاده";

    ExecutionContext persian = new ExecutionContext(arabicKafYeh);
    persian.store(LanguageResult.class, languageStep.execute(persian));
    assertThat(step.execute(persian).normalizedQuery()).isEqualTo("کفش مشکی پیاده");

    String arabic = "كتاب عربي جميل";
    ExecutionContext arabicContext = new ExecutionContext(arabic);
    arabicContext.store(LanguageResult.class, new LanguageResult("ar"));
    assertThat(step.execute(arabicContext).normalizedQuery()).isEqualTo(arabic);
  }

  @Test
  void declaredLanguageGuidesNormalizationOnlyWhenNothingWasDetected() {
    var input = MultilingualInput.of("كي", "fa", null, InputSource.API);
    ExecutionContext context = new ExecutionContext("id", input);
    var undetermined =
        new io.qwenbridge.language.LanguageAnalysis(
            io.qwenbridge.core.language.LanguageDetection.undetermined("test"),
            io.qwenbridge.core.request.LanguageTag.parse("fa"),
            "unknown",
            false,
            List.of(),
            io.qwenbridge.core.request.TextDirection.RTL,
            false);
    context.store(LanguageResult.class, new LanguageResult("fa", undetermined));
    assertThat(step.execute(context).normalizedQuery()).isEqualTo("کی");

    var detectedArabic =
        new io.qwenbridge.language.LanguageAnalysis(
            new io.qwenbridge.core.language.LanguageDetection(
                io.qwenbridge.core.request.LanguageTag.parse("ar").orElseThrow(),
                io.qwenbridge.core.confidence.Confidence.modelScore(0.9),
                "test"),
            io.qwenbridge.core.request.LanguageTag.parse("fa"),
            "ar",
            false,
            List.of(),
            io.qwenbridge.core.request.TextDirection.RTL,
            false);
    context.store(LanguageResult.class, new LanguageResult("ar", detectedArabic));
    assertThat(step.execute(context).normalizedQuery())
        .as("a declared language never overrides a detection")
        .isEqualTo("كي");
  }

  @Test
  void shouldExposeNormalizedInputAsResultType() {
    NormalizationStep bare = new NormalizationStep(new DefaultInputNormalizer(List.of()));

    assertThat(bare.name()).isEqualTo("NormalizationStep");
    assertThat(bare.order()).isEqualTo(15);
    assertThat(bare.resultType()).isEqualTo(NormalizedInput.class);
  }
}
