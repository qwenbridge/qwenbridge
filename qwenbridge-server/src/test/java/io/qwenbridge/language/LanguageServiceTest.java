package io.qwenbridge.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LanguageServiceTest {

  private final LanguageService languageService = new LanguageService();

  @ParameterizedTest(name = "[{index}] {1}: {0}")
  @MethodSource("realisticLanguageQueries")
  void shouldDetectExpectedLanguageForRealisticQueries(String query, String expectedLanguage) {
    assertThat(languageService.detect(query)).isEqualTo(expectedLanguage);
  }

  /**
   * QB-008 replaced the former "short Latin text is English" fallback. Such text is either
   * identified by the model (with a model score) or stays undetermined; English is never a default.
   */
  @ParameterizedTest(name = "[{index}] no English default: {0}")
  @MethodSource("latinEnglishFallbackQueries")
  void shouldNotAssumeEnglishForAmbiguousLatinQueries(String query) {
    var detection = languageService.detectWhole(query);
    if (detection.determined()) {
      assertThat(detection.confidence().kind())
          .isEqualTo(io.qwenbridge.core.confidence.Confidence.Kind.MODEL_DECISION);
    } else {
      assertThat(languageService.detect(query)).isEqualTo("unknown");
    }
    assertThat(detection.detector()).isNotEqualTo("english-default");
  }

  @ParameterizedTest(name = "[{index}] single word stays undetermined: {0}")
  @org.junit.jupiter.params.provider.ValueSource(strings = {"laptop", "table", "desk", "bord"})
  void singleAmbiguousWordsStayUndetermined(String query) {
    assertThat(languageService.detect(query)).isEqualTo("unknown");
    assertThat(languageService.detectWhole(query).confidence().kind())
        .isEqualTo(io.qwenbridge.core.confidence.Confidence.Kind.UNKNOWN);
  }

  @Test
  void sentencesAreDecidedByTheModelWithoutAFakeScore() {
    var detection =
        languageService.detectWhole("Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?");
    assertThat(detection.language().value()).isEqualTo("sv");
    assertThat(detection.confidence().kind())
        .isEqualTo(io.qwenbridge.core.confidence.Confidence.Kind.MODEL_DECISION);
    assertThat(detection.confidence().value()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] short text never reaches the model: {0}")
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"cheap office chair", "black shoes", "Nike", "usb c charger"})
  void shortLatinTextStaysUndetermined(String query) {
    assertThat(languageService.detect(query)).isEqualTo("unknown");
  }

  @Test
  void mixedLanguageInputIsSegmentedNotForcedIntoOneLanguage() {
    var analysis =
        languageService.analyze(
            "کفش مشکی برای زمستان" + " best wireless headphones for working from home",
            java.util.Optional.empty());
    assertThat(analysis.mixed()).isTrue();
    assertThat(analysis.detection().determined()).isFalse();
    assertThat(analysis.segments()).hasSize(2);
    assertThat(analysis.segments().get(0).detection().language().value()).isEqualTo("fa");
    assertThat(analysis.primaryLanguage()).isEqualTo("fa");
    assertThat(analysis.requiresTranslationTo("en")).isTrue();
    assertThat(analysis.direction()).isEqualTo(io.qwenbridge.core.request.TextDirection.MIXED);
  }

  @Test
  void latinBrandInsideNonLatinSentenceDoesNotMakeItMixed() {
    var analysis =
        languageService.analyze(
            "ما هي أفضل سماعات Nike" + " لاسلكية للعمل من" + " المنزل؟",
            java.util.Optional.empty());
    assertThat(analysis.primaryLanguage()).isEqualTo("ar");
    assertThat(analysis.mixed()).isFalse();
  }

  @Test
  void deterministicFallbackIsUsedWhenTheModelIsDisabled() {
    LanguageService heuristicOnly = new LanguageService(false, java.util.List.of("en", "fa", "sv"));
    assertThat(heuristicOnly.detect("کفش مشکی")).isEqualTo("fa");
    assertThat(heuristicOnly.detect("東京のホテル")).isEqualTo("ja");
    assertThat(heuristicOnly.detect("What are the best wireless headphones?")).isEqualTo("unknown");
    assertThat(heuristicOnly.detectWhole("wireless headphones").detector())
        .isEqualTo(io.qwenbridge.core.language.ScriptHeuristicLanguageDetector.ID);
  }

  @Test
  void configuredLanguagesMarkResultsWithoutRestrictingDetection() {
    var swedish =
        languageService.analyze(
            "Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?", java.util.Optional.empty());
    var german =
        languageService.analyze(
            "Welche kabellosen Kopfhörer eignen sich am besten für die Arbeit zu Hause?",
            java.util.Optional.empty());
    assertThat(swedish.configuredLanguage()).isTrue();
    assertThat(german.primaryLanguage()).isEqualTo("de");
    assertThat(german.configuredLanguage()).isFalse();
  }

  @Test
  void invalidConfiguredLanguageFailsFast() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new LanguageService(true, java.util.List.of("en", "english")))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest(name = "[{index}] unknown: {0}")
  @MethodSource("unknownQueries")
  void shouldReturnUnknownForBlankSymbolicOrNumericOnlyQueries(String query) {
    assertThat(languageService.detect(query)).isEqualTo("unknown");
  }

  @Test
  void shouldReturnUnknownForNullQuery() {
    assertThat(languageService.detect(null)).isEqualTo("unknown");
  }

  private static Stream<Arguments> realisticLanguageQueries() {
    return Stream.of(
        Arguments.of("What are the best wireless headphones for working from home?", "en"),
        Arguments.of("بهترین هدفون بی‌سیم برای کار کردن در خانه چیست؟", "fa"),
        Arguments.of("ما هي أفضل سماعات لاسلكية للعمل من المنزل؟", "ar"),
        Arguments.of("自宅で仕事をするための最高のワイヤレスヘッドホンは何ですか？", "ja"),
        Arguments.of("哪些无线耳机最适合在家办公？", "zh"),
        Arguments.of("Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?", "sv"),
        Arguments.of(
            "Welche kabellosen Kopfhörer eignen sich am besten für die Arbeit zu Hause?", "de"),
        Arguments.of(
            "Quels écouteurs sans fil sont les meilleurs pour travailler à domicile ?", "fr"),
        Arguments.of("¿Qué auriculares inalámbricos son mejores para trabajar desde casa?", "es"),
        Arguments.of("Evden çalışmak için en iyi kablosuz kulaklıklar hangileridir?", "tr"),
        Arguments.of("Welke draadloze hoofdtelefoon is het beste om thuis te werken?", "nl"));
  }

  private static Stream<Arguments> latinEnglishFallbackQueries() {
    return Stream.of(
        Arguments.of("best gaming laptop under 1500 euro"),
        Arguments.of("gaming laptop"),
        Arguments.of("laptop"),
        Arguments.of("table"),
        Arguments.of("desk"),
        Arguments.of("dining table for 4 people"),
        Arguments.of("wireless headphones"),
        Arguments.of("best wireless headphones for working from home"),
        Arguments.of("iphone case"),
        Arguments.of("mechanical keyboard"),
        Arguments.of("best gamng labtop under 1500 euro"),
        Arguments.of("cheap office chair"),
        Arguments.of("monitor 27 inch 4k"),
        Arguments.of("usb c charger"));
  }

  private static Stream<Arguments> unknownQueries() {
    return Stream.of(
        Arguments.of(""),
        Arguments.of("   "),
        Arguments.of("12345"),
        Arguments.of("12345 !!!"),
        Arguments.of("!!! ??? ..."),
        Arguments.of("€1500"),
        Arguments.of("🙂🙂🙂"));
  }
}
