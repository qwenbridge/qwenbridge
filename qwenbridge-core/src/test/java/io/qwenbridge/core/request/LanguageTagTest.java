package io.qwenbridge.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTagTest {

  @ParameterizedTest
  @CsvSource({
    "en, en",
    "EN, en",
    "fa, fa",
    "fa-IR, fa-IR",
    "fa-ir, fa-IR",
    "sv-SE, sv-SE",
    "zh-Hant-TW, zh-Hant-TW",
    "zh-hant-tw, zh-Hant-TW",
    "und, und",
    "gsw, gsw",
    "es-419, es-419",
    "' sv ', sv"
  })
  void acceptsAndCanonicalizesWellFormedTags(String raw, String canonical) {
    assertEquals(canonical, LanguageTag.parse(raw).orElseThrow().value());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "english", "sv_SE", "e1", "en-", "-en", "xq", "en--US", "x-private"})
  void rejectsMalformedOrUnsupportedTags(String raw) {
    assertTrue(LanguageTag.parse(raw).isEmpty(), raw);
    assertFalse(LanguageTag.isValid(raw), raw);
  }

  @Test
  void rejectsOverlongInput() {
    assertTrue(LanguageTag.parse("en-" + "a1b2c3d4-".repeat(10)).isEmpty());
  }

  @Test
  void constructorRefusesInvalidValues() {
    assertThrows(IllegalArgumentException.class, () -> new LanguageTag("english"));
  }

  @Test
  void exposesPrimaryLanguageAndUndetermined() {
    assertEquals("fa", LanguageTag.parse("fa-IR").orElseThrow().primaryLanguage());
    assertTrue(LanguageTag.UNDETERMINED.isUndetermined());
    assertFalse(LanguageTag.parse("sv").orElseThrow().isUndetermined());
  }

  @Test
  void persianSwedishAndHebrewAreKnownIsoLanguages() {
    for (String tag : new String[] {"fa", "sv", "he", "en", "ar", "ja"}) {
      assertTrue(LanguageTag.isValid(tag), tag);
    }
  }
}
