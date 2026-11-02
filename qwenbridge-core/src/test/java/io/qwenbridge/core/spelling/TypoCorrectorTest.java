package io.qwenbridge.core.spelling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * QB-291: the typo engine with small fixture word lists. The fixtures are test data for a real
 * local provider (the dictionary provider); no result is claimed for a language without one.
 */
class TypoCorrectorTest {

  static Lexicon lexicon(String text) {
    try {
      return Lexicon.parse(new StringReader(text));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static final DictionaryCorrectionProvider FIXTURES =
      new DictionaryCorrectionProvider(
          "fixture-dictionaries",
          Map.of(
              new LanguageTag("en"),
              lexicon(
                  "black\t900\n"
                      + "shoes\t800\n"
                      + "shoe\t500\n"
                      + "size\t700\n"
                      + "under\t900\n"
                      + "red\t800\n"
                      + "jacket\t400\n"
                      + "example\t300\n"
                      + "bad\t100\n"
                      + "bag\t100\n"
                      + "and\t999\n"
                      + "black shoes\n"),
              new LanguageTag("sv"),
              lexicon("röd\t500\njacka\t400\nsvart\t500\nskor\t400\nröd jacka\n"),
              new LanguageTag("fa"),
              lexicon("کفش\t500\nمشکی\t400\nقرمز\t300\nکفش مشکی\n")),
          Optional.of(lexicon("iPhone 16 Pro\nSamsung Galaxy\nSony\n")));

  static final List<CorrectionProvider> PROVIDERS = List.of(FIXTURES);

  static CorrectionResult run(
      String text, TypoSettings settings, TypoCorrector.Detection detection) {
    return TypoCorrector.correct(text, text, settings, detection, PROVIDERS);
  }

  static CorrectionResult safe(String text, String... languages) {
    return run(
        text,
        TypoSettings.of(true, List.of(languages), CorrectionMode.SAFE_AUTO_CORRECT),
        TypoCorrector.Detection.none());
  }

  @Test
  void nothingIsEnabledOrAssumedByDefault() {
    var disabled = run("blak shoes", TypoSettings.disabled(), TypoCorrector.Detection.none());
    assertEquals(CorrectionResult.Status.DISABLED, disabled.status());
    assertEquals("blak shoes", disabled.corrected());
    var noLanguages =
        run(
            "blak shoes",
            TypoSettings.of(true, List.of(), CorrectionMode.SAFE_AUTO_CORRECT),
            TypoCorrector.Detection.none());
    assertEquals(CorrectionResult.Status.UNCONFIGURED, noLanguages.status());
    assertEquals(CorrectionMode.DISABLED, noLanguages.mode());
    assertEquals("blak shoes", noLanguages.corrected());
    assertTrue(noLanguages.corrections().isEmpty());
  }

  @Test
  void oneConfiguredLanguage() {
    var r = safe("röd jakca", "sv");
    assertEquals(CorrectionResult.Status.CORRECTED, r.status());
    assertEquals("röd jacka", r.corrected());
    assertEquals("röd jakca", r.original());
    var c = r.corrections().getFirst();
    assertEquals("jakca", c.token());
    assertEquals("sv", c.language());
    assertTrue(c.applied());
    assertTrue(c.reason().contains("fits the neighbouring word"), c.reason());
    // Only Swedish is enabled: an English typo is not corrected.
    assertEquals("blak shoes", safe("blak shoes", "sv").corrected());
  }

  @Test
  void aWordKnownInAnyEnabledLanguageIsNeverCorrected() {
    // QB-294 runtime finding: the detector labelled an English query Swedish; with en and sv
    // enabled, the English "red" became the Swedish "röd". A known word is not a typo.
    var r =
        run(
            "I need a red winter jacket",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("sv")), true, false));
    assertEquals("I need a red winter jacket", r.corrected());
    assertTrue(r.corrections().stream().noneMatch(c -> c.token().equals("red")), r.toString());
    // Unknown words are still corrected in the detected language.
    var typo =
        run(
            "röd jakca",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("sv")), true, false));
    assertEquals("röd jacka", typo.corrected());
  }

  @Test
  void anotherEnabledLanguageWithMoreKnownWordsOverrulesAStatisticalDetection() {
    // QB-295 E2E finding: "red wintr jaket under" was detected as Swedish (enabled); English knows
    // more of its words, so English typos are corrected and the decision is explained.
    var r =
        run(
            "red wintr jaket under 100 EUR",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("sv")), true, false));
    assertEquals(List.of("en"), r.activeLanguages());
    assertTrue(r.explanations().stream().anyMatch(e -> e.contains("more known words are en")));
    // A Swedish query detected as Swedish stays Swedish.
    var sv =
        run(
            "röd jakca",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("sv")), true, false));
    assertEquals(List.of("sv"), sv.activeLanguages());
  }

  @Test
  void severalLanguagesAndMixedInput() {
    var r =
        run(
            "röd jakca och blak shoes",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.empty(), false, true));
    assertEquals("röd jacka och black shoes", r.corrected());
    assertEquals(List.of("en", "sv"), r.activeLanguages());
    assertEquals(
        List.of("sv", "en"),
        r.corrections().stream().map(CorrectionResult.TokenCorrection::language).toList());
  }

  @Test
  void unsupportedInvalidAndMissingProvidersAreExplicit() {
    var r = safe("blak shoes", "en", "de", "not a tag!");
    var status = new HashMap<String, LanguageSupport.Status>();
    r.languages().forEach(l -> status.put(l.configured(), l.status()));
    assertEquals(LanguageSupport.Status.SUPPORTED, status.get("en"));
    assertEquals(LanguageSupport.Status.PROVIDER_UNAVAILABLE, status.get("de"));
    assertEquals(LanguageSupport.Status.INVALID_IDENTIFIER, status.get("not a tag!"));
    assertEquals("black shoes", r.corrected());
    // Only languages without a provider: nothing is substituted, even though English could correct
    // it.
    var none = safe("blak shoes", "de", "fr");
    assertEquals(CorrectionResult.Status.NO_PROVIDER, none.status());
    assertEquals("blak shoes", none.corrected());
  }

  @Test
  void aReliablyDetectedLanguageOutsideTheConfiguredSetIsNotCorrected() {
    var r =
        run(
            "schwarze schuhe",
            TypoSettings.of(true, List.of("en"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("de")), true, false));
    assertEquals(CorrectionResult.Status.LANGUAGE_NOT_ENABLED, r.status());
    assertEquals("schwarze schuhe", r.corrected());
    // A detector misled by the typo itself ("blak" reads as Afrikaans): the query's known English
    // words decide, and the explanation says so.
    var misled =
        run(
            "blak shoes under 100 EUR",
            TypoSettings.of(true, List.of("en"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("af")), true, false));
    assertEquals("black shoes under 100 EUR", misled.corrected());
    assertTrue(misled.explanations().getFirst().contains("The detector reported af"));
    var matching =
        run(
            "blak shoes",
            TypoSettings.of(true, List.of("en", "sv"), CorrectionMode.SAFE_AUTO_CORRECT),
            new TypoCorrector.Detection(Optional.of(new LanguageTag("en")), true, false));
    assertEquals(List.of("en"), matching.activeLanguages());
    assertEquals("black shoes", matching.corrected());
  }

  @Test
  void nonLatinScripts() {
    var r = safe("کفس مشکی", "fa");
    assertEquals("کفش مشکی", r.corrected());
    assertEquals("fa", r.corrections().getFirst().language());
    // A Persian word is never "corrected" towards an English word, and vice versa.
    assertEquals("کفس مشکی", safe("کفس مشکی", "en").corrected());
  }

  @Test
  void protectedInformationIsNeverChanged() {
    String text =
        "redis03-pt1 10.0.0.1 a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d https://exmaple.com/blak "
            + "john@exmaple.com host.exmaple.se \"exmaple blak\" EUR 99.95 SKU-123 pod-blak";
    var r = safe(text, "en");
    assertEquals(text, r.corrected());
    assertTrue(r.protectedTokens() > 5);
    assertTrue(r.corrections().isEmpty(), r.corrections().toString());
  }

  @Test
  void productNamesComeFromDomainVocabulary() {
    var r = safe("iphnoe 16 pro", "en");
    assertEquals("iPhone 16 pro", r.corrected());
    assertTrue(r.corrections().getFirst().reason().contains("domain vocabulary"));
    assertEquals("Samsung Galaxy", safe("Samsung Galxay", "en").corrected());
  }

  @Test
  void hardConstraintsAreNotModified() {
    var r = safe("blak shoes size 42 under 100 EUR", "en");
    assertEquals("black shoes size 42 under 100 EUR", r.corrected());
    assertEquals(1, r.corrections().size());
  }

  @Test
  void falsePositiveProtection() {
    // Correct words are left alone.
    assertEquals(
        CorrectionResult.Status.NO_CHANGES, safe("black shoes under example", "en").status());
    // Two equally good candidates (bad, bag): suggested, not applied.
    var tie = safe("baf", "en");
    assertEquals(CorrectionResult.Status.SUGGESTED, tie.status());
    assertEquals("baf", tie.corrected());
    assertFalse(tie.corrections().getFirst().applied());
    // Unknown words with no close candidate are left alone.
    assertEquals("Göteborg", safe("Göteborg", "sv").corrected());
    // Suggest-only never changes the text.
    var suggest =
        run(
            "blak shoes",
            TypoSettings.of(true, List.of("en"), CorrectionMode.SUGGEST_ONLY),
            TypoCorrector.Detection.none());
    assertEquals(CorrectionResult.Status.SUGGESTED, suggest.status());
    assertEquals("blak shoes", suggest.corrected());
    assertEquals("black", suggest.corrections().getFirst().replacement());
    // Safe mode refuses a two-edit change on a short word; aggressive mode applies it.
    assertEquals("bllak shoes", safe("bllak shoes", "en").corrected());
    var aggressive =
        run(
            "bllak shoes",
            TypoSettings.of(true, List.of("en"), CorrectionMode.AGGRESSIVE),
            TypoCorrector.Detection.none());
    assertEquals("black shoes", aggressive.corrected());
  }

  @Test
  void concurrentRequestsGetIdenticalResults() throws Exception {
    var settings =
        TypoSettings.of(true, List.of("en", "sv", "fa"), CorrectionMode.SAFE_AUTO_CORRECT);
    String[] inputs = {"blak shoes", "röd jakca", "کفس مشکی", "iphnoe 16 pro", "redis03-pt1"};
    Map<String, String> expected = new HashMap<>();
    for (String in : inputs)
      expected.put(in, run(in, settings, TypoCorrector.Detection.none()).corrected());
    try (var pool = Executors.newFixedThreadPool(8)) {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++)
        futures.add(
            pool.submit(
                (Callable<Boolean>)
                    () -> {
                      for (int i = 0; i < 500; i++)
                        for (String in : inputs)
                          if (!expected
                              .get(in)
                              .equals(
                                  run(in, settings, TypoCorrector.Detection.none()).corrected()))
                            return false;
                      return true;
                    }));
      for (var f : futures) assertTrue(f.get());
    }
  }

  @Test
  void latencyWithALargeDictionary() {
    var random = new Random(42);
    var words = new StringBuilder();
    for (int i = 0; i < 50_000; i++) {
      int len = 4 + random.nextInt(8);
      for (int j = 0; j < len; j++) words.append((char) ('a' + random.nextInt(26)));
      words.append('\t').append(1 + random.nextInt(1000)).append('\n');
    }
    words.append("black\t900\nshoes\t900\n");
    var provider =
        new DictionaryCorrectionProvider(
            "synthetic",
            Map.of(new LanguageTag("en"), lexicon(words.toString())),
            Optional.empty());
    var settings = TypoSettings.of(true, List.of("en"), CorrectionMode.SAFE_AUTO_CORRECT);
    long[] ns = new long[300];
    for (int i = 0; i < ns.length; i++) {
      long s = System.nanoTime();
      TypoCorrector.correct(
          "blak shoes size 42",
          "blak shoes size 42",
          settings,
          TypoCorrector.Detection.none(),
          List.of(provider));
      ns[i] = System.nanoTime() - s;
    }
    Arrays.sort(ns);
    double p50 = ns[150] / 1e6, p95 = ns[284] / 1e6;
    System.out.printf(
        "typo-latency dictionary=50002 words p50=%.2fms p95=%.2fms heap~%dKB%n",
        p50, p95, provider.descriptor().approximateBytes() / 1024);
    assertTrue(p95 < 50, "p95 " + p95 + " ms");
  }
}
