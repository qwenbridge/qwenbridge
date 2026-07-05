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

  @ParameterizedTest(name = "[{index}] english fallback: {0}")
  @MethodSource("latinEnglishFallbackQueries")
  void shouldFallbackToEnglishForAmbiguousLatinQueries(String query) {
    assertThat(languageService.detect(query)).isEqualTo("en");
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
