package io.qwenbridge.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchTextNormalizerTest {

  private static final Optional<LanguageTag> NONE = Optional.empty();
  private static final Optional<LanguageTag> FA = LanguageTag.parse("fa");
  private static final Optional<LanguageTag> AR = LanguageTag.parse("ar");
  private static final Optional<LanguageTag> SV = LanguageTag.parse("sv");

  private static String norm(String text, Optional<LanguageTag> language) {
    return SearchTextNormalizer.normalize(text, language).normalized();
  }

  // --- QB-009: canonical, not compatibility, normalization -------------------------------

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "full-width product code | ＳＫＵ１２３",
        "superscripts | x² + y²",
        "ligature | ﬁle",
        "circled digit | step ①",
        "roman numeral | Chapter Ⅳ",
        "symbols and identifiers | C++ and C# SKU-12_3/B",
        "url text stays encoded | 100%25 off at https://ex.com/a%20b?q=1&x=2",
        "html entities stay literal | &lt;b&gt; &amp; &#60;",
        "currency and units | 1200 SEK €99 5kg 20°C ﷼"
      })
  void compatibilityCharactersAndIdentifiersAreNotRewritten(String name, String text) {
    assertEquals(text, norm(text, NONE), name);
  }

  @Test
  void decomposedSequencesAreCanonicallyComposed() {
    assertEquals("löparskor café", norm("löparskor café", SV));
  }

  // --- QB-011: control characters -------------------------------------------------------

  @Test
  void disallowedControlsAreRemovedButJoinersAndSelectorsKept() {
    assertEquals("abcdef", norm("abc‮def", NONE));
    assertEquals("hello", norm("﻿hello", NONE));
    assertEquals("ab", norm("a\u0000b\u0007", NONE));
    assertEquals("co-op", norm("co-­op", NONE));
    assertEquals("Trojan", norm("⁦Trojan⁩‎", NONE));
    String emoji = "🏃‍♀️"; // ZWJ sequence with variation selector
    assertEquals(emoji, norm(emoji, NONE));
    String flag = "🏴󠁧󠁢󠁥󠁮󠁧󠁿";
    assertEquals(flag, norm(flag, NONE));
  }

  @Test
  void tabsAndNewlinesBecomeSpacesNotDeleted() {
    assertEquals("a b c", norm("a\tb\nc", NONE));
  }

  // --- QB-012: whitespace and Persian variants ---------------------------------------------

  @Test
  void whitespaceRunsCollapseIncludingUnicodeSpaces() {
    assertEquals("a b c", norm("  a \t\n b　c  ", NONE));
    assertEquals("1 200 kr", norm("1 200 kr", NONE));
  }

  @Test
  void persianVariantsAreUnifiedOnlyForPersian() {
    String arabicForms = "كيف چرمي";
    assertEquals("کیف چرمی", norm(arabicForms, FA));
    assertEquals(arabicForms, norm(arabicForms, AR), "Arabic text must not be Persianized");
    assertEquals(arabicForms, norm(arabicForms, NONE), "unknown language must not be rewritten");
    assertEquals("کیف", norm("کـــیف", FA));
  }

  @Test
  void zwnjIsPreservedInsideWordsAndTidiedElsewhere() {
    String halfSpace = "می‌خوام";
    assertEquals(halfSpace, norm(halfSpace, FA));
    assertEquals(halfSpace, norm("می‌‌خوام", FA));
    assertEquals("می خوام", norm("می ‌خوام", FA));
    assertEquals("می", norm("‌می‌", FA));
  }

  // --- QB-014: semantic preservation of operators and negation ---------------------------

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "black shoes, not leather, under 1200 SEK",
        "memory usage above 85% during the last 1h",
        "bot score below 10, excluding trusted sources",
        "svarta skor, inte läder, högst 1200 kr",
        "utan läder och ej begagnade",
        "کفش بدون چرم زیر ۱۲۰۰" + " کرون",
        "price >= 100 && price <= 200 || size != 42"
      })
  void negationOperatorsAndNumbersSurvive(String text) {
    assertEquals(text, norm(text, NONE));
    assertEquals(text, norm(text, FA));
  }

  @Test
  void originalIsRetainedAndTraceIsRecorded() {
    SearchTextNormalizer.Result result = SearchTextNormalizer.normalize("  a‮ b ", NONE);
    assertEquals("  a‮ b ", result.original());
    assertEquals("a b", result.normalized());
    assertTrue(result.changed());
    assertEquals(
        List.of("disallowed-controls", "unicode-nfc", "zero-width-non-joiner", "whitespace"),
        result.steps().stream().map(SearchTextNormalizer.Step::name).toList());
    assertTrue(result.steps().get(0).changed());
    assertFalse(result.steps().get(1).changed());
  }

  @Test
  void nullIsTreatedAsEmpty() {
    assertEquals("", norm(null, NONE));
  }

  // --- Property tests with a fixed seed (reproducible) -----------------------------------

  private static final long SEED = 20261031L;
  private static final int SAMPLES = 4000;

  private static final String[] ALPHABET = {
    "a", "Z", "0", "7", " ", "  ", "\t", "\n", " ", "　", "-", "%", "&", ";", "#", "+", "ä", "ö",
    "å", "é", "م", "ی", "ي", "ى", "ك", "ک", "ـ", "‌", "‍", "َ", "۱", "١", "\u0000", "\u0007", "‮",
    "‎", "﻿", "­", "⁦", "👟", "️", "Ｓ", "²", "ﬁ", "€", "東", "ホ", "ש", "not", "بدون"
  };

  private static List<String> samples() {
    Random random = new Random(SEED);
    List<String> samples = new ArrayList<>(SAMPLES);
    for (int n = 0; n < SAMPLES; n++) {
      StringBuilder sb = new StringBuilder();
      int length = random.nextInt(24);
      for (int i = 0; i < length; i++) {
        sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      }
      samples.add(sb.toString());
    }
    return samples;
  }

  private static List<Optional<LanguageTag>> languages() {
    return List.of(NONE, FA, AR, SV);
  }

  @Test
  void propertyNormalizationIsIdempotent() {
    for (String sample : samples()) {
      for (Optional<LanguageTag> language : languages()) {
        String once = norm(sample, language);
        assertEquals(once, norm(once, language), () -> "not idempotent for " + escape(sample));
      }
    }
  }

  @Test
  void propertyOriginalIsNeverModified() {
    for (String sample : samples()) {
      String copy = new String(sample);
      SearchTextNormalizer.Result result = SearchTextNormalizer.normalize(sample, FA);
      assertEquals(copy, result.original());
      assertSame(sample.length(), copy.length());
    }
  }

  @Test
  void propertyDigitsAreNeverAlteredOrDropped() {
    for (String sample : samples()) {
      for (Optional<LanguageTag> language : languages()) {
        assertEquals(
            digits(sample),
            digits(norm(sample, language)),
            () -> "digits changed: " + escape(sample));
      }
    }
  }

  @Test
  void propertyLettersAreKeptExceptDocumentedPersianUnification() {
    for (String sample : samples()) {
      for (Optional<LanguageTag> language : List.of(NONE, AR, SV)) {
        String nfc = Normalizer.normalize(stripControls(sample), Normalizer.Form.NFC);
        assertEquals(
            letters(nfc),
            letters(norm(sample, language)),
            () -> "letters changed: " + escape(sample));
      }
    }
  }

  @Test
  void propertyOutputHasNoDisallowedControlsOrEdgeWhitespace() {
    for (String sample : samples()) {
      for (Optional<LanguageTag> language : languages()) {
        String out = norm(sample, language);
        assertTrue(out.codePoints().noneMatch(SearchTextNormalizer::isDisallowedControl));
        assertFalse(out.contains("  "), () -> "double space for " + escape(sample));
        assertEquals(out, out.strip(), () -> "edge whitespace for " + escape(sample));
      }
    }
  }

  private static String stripControls(String text) {
    StringBuilder out = new StringBuilder();
    text.codePoints()
        .filter(cp -> !SearchTextNormalizer.isDisallowedControl(cp))
        .forEach(out::appendCodePoint);
    return out.toString();
  }

  private static List<Integer> digits(String text) {
    return text.codePoints().filter(Character::isDigit).boxed().toList();
  }

  private static List<Integer> letters(String text) {
    return text.codePoints().filter(Character::isLetter).boxed().toList();
  }

  private static String escape(String text) {
    StringBuilder out = new StringBuilder();
    text.codePoints()
        .forEach(
            cp -> out.append(cp < 0x20 || cp > 0x7e ? String.format("\\u%04x", cp) : (char) cp));
    return out.toString();
  }
}
