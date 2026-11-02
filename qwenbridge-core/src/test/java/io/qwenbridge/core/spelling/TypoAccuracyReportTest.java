package io.qwenbridge.core.spelling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * QB-291 K: accuracy and false-positive report of the engine on a labelled set with fixture
 * dictionaries (English, Swedish, Persian, domain vocabulary). It measures the engine's decisions,
 * not the quality of any real dictionary: production accuracy depends on the dictionaries an
 * application installs. Results: {@code target/benchmarks/typo-accuracy.json}.
 */
class TypoAccuracyReportTest {

  static final DictionaryCorrectionProvider PROVIDER =
      new DictionaryCorrectionProvider(
          "fixture-dictionaries",
          Map.of(
              new LanguageTag("en"),
              TypoCorrectorTest.lexicon(
                  "black\t900\n"
                      + "shoes\t800\n"
                      + "size\t700\n"
                      + "under\t900\n"
                      + "red\t800\n"
                      + "jacket\t400\n"
                      + "white\t700\n"
                      + "laptop\t600\n"
                      + "headphones\t300\n"
                      + "wireless\t300\n"
                      + "keyboard\t300\n"
                      + "cheap\t400\n"
                      + "leather\t300\n"
                      + "running\t500\n"
                      + "with\t999\n"
                      + "for\t999\n"
                      + "and\t999\n"
                      + "black shoes\n"
                      + "running shoes\n"
                      + "wireless headphones\n"),
              new LanguageTag("sv"),
              TypoCorrectorTest.lexicon(
                  "röd\t500\n"
                      + "jacka\t400\n"
                      + "svart\t500\n"
                      + "skor\t400\n"
                      + "billig\t300\n"
                      + "vit\t400\n"
                      + "byxor\t300\n"
                      + "röd jacka\n"
                      + "svarta skor\n"),
              new LanguageTag("fa"),
              TypoCorrectorTest.lexicon("کفش\t500\nمشکی\t400\nقرمز\t300\nارزان\t200\nکفش مشکی\n")),
          Optional.of(
              TypoCorrectorTest.lexicon("iPhone 16 Pro\nSamsung Galaxy\nSony\nNike\nAdidas\n")));

  /** typo -> expected; clean -> unchanged; protected -> unchanged. */
  record Case(String kind, String input, String expected) {}

  static final List<Case> CASES =
      List.of(
          new Case("typo", "blak shoes", "black shoes"),
          new Case("typo", "black shoees", "black shoes"),
          new Case("typo", "red jakcet", "red jacket"),
          new Case("typo", "wireles headphones", "wireless headphones"),
          new Case("typo", "cheap laptpo", "cheap laptop"),
          new Case("typo", "leathr jacket", "leather jacket"),
          new Case("typo", "runnign shoes", "running shoes"),
          new Case("typo", "white keybaord", "white keyboard"),
          new Case("typo", "röd jakca", "röd jacka"),
          new Case("typo", "svart skro", "svart skor"),
          new Case("typo", "billig jakca", "billig jacka"),
          new Case("typo", "vit byxro", "vit byxor"),
          new Case("typo", "کفس مشکی", "کفش مشکی"),
          new Case("typo", "کفش مشکب", "کفش مشکی"),
          new Case("typo", "iphnoe 16 pro", "iPhone 16 pro"),
          new Case("typo", "Samsung Galxay", "Samsung Galaxy"),
          new Case("typo", "Adiads running shoes", "Adidas running shoes"),
          new Case("clean", "black shoes size 42", null),
          new Case("clean", "wireless headphones under 200 EUR", null),
          new Case("clean", "röd jacka", null),
          new Case("clean", "svarta skor", null),
          new Case("clean", "کفش مشکی ارزان", null),
          new Case("clean", "iPhone 16 Pro", null),
          new Case("clean", "Göteborg", null),
          new Case("clean", "Stockholm laptop", null),
          new Case("clean", "kubernetes cluster", null),
          new Case("clean", "the best laptop for students", null),
          new Case("clean", "Nike Pegasus", null),
          new Case("protected", "redis03-pt1 black shoes", null),
          new Case("protected", "10.0.0.1 laptop", null),
          new Case("protected", "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", null),
          new Case("protected", "https://shoop.example/blak", null),
          new Case("protected", "laptpo@example.com", null),
          new Case("protected", "\"blak shoees\"", null),
          new Case("protected", "SKU-4411 jacket", null),
          new Case("protected", "under 99.95 EUR", null),
          new Case("protected", "size 42 shoes", null),
          new Case("protected", "api-gatewya.internal", null));

  @Test
  void accuracyAndFalsePositives() throws Exception {
    var settings =
        TypoSettings.of(true, List.of("en", "sv", "fa"), CorrectionMode.SAFE_AUTO_CORRECT);
    int typos = 0,
        fixed = 0,
        wrong = 0,
        missed = 0,
        clean = 0,
        falsePositives = 0,
        protectedCases = 0,
        protectedChanged = 0;
    StringBuilder rows = new StringBuilder();
    for (Case c : CASES) {
      var r =
          TypoCorrector.correct(
              c.input(), c.input(), settings, TypoCorrector.Detection.none(), List.of(PROVIDER));
      String out = r.corrected();
      switch (c.kind()) {
        case "typo" -> {
          typos++;
          if (out.equals(c.expected())) fixed++;
          else if (!out.equals(c.input())) wrong++;
          else missed++;
        }
        case "clean" -> {
          clean++;
          if (!out.equals(c.input())) falsePositives++;
        }
        default -> {
          protectedCases++;
          if (!out.equals(c.input())) protectedChanged++;
        }
      }
      rows.append(
          String.format(
              Locale.ROOT,
              "    {\"kind\": \"%s\", \"input\": \"%s\", \"output\": \"%s\", \"status\":"
                  + " \"%s\"},%n",
              c.kind(),
              c.input().replace("\"", "\\\""),
              out.replace("\"", "\\\""),
              r.status()));
    }
    double precision = fixed + wrong == 0 ? 1 : fixed / (double) (fixed + wrong);
    double recall = fixed / (double) typos;
    double fpRate = falsePositives / (double) clean;
    String json =
        String.format(
            Locale.ROOT,
            "{%n  \"mode\": \"SAFE_AUTO_CORRECT\",%n  \"dictionaries\": \"test fixtures (en, sv,"
                + " fa, domain)\",%n  \"typos\": %d, \"fixed\": %d, \"wrong\": %d, \"missed\":"
                + " %d,%n  \"precision\": %.3f, \"recall\": %.3f,%n  \"clean\": %d,"
                + " \"falsePositives\": %d, \"falsePositiveRate\": %.3f,%n  \"protected\": %d,"
                + " \"protectedChanged\": %d,%n  \"cases\": [%n%s  ]%n}%n",
            typos,
            fixed,
            wrong,
            missed,
            precision,
            recall,
            clean,
            falsePositives,
            fpRate,
            protectedCases,
            protectedChanged,
            rows.substring(0, rows.length() - 2) + "\n");
    Files.createDirectories(Path.of("target", "benchmarks"));
    Files.writeString(Path.of("target", "benchmarks", "typo-accuracy.json"), json);
    System.out.printf(
        Locale.ROOT,
        "typo-accuracy typos=%d fixed=%d wrong=%d missed=%d precision=%.3f recall=%.3f clean=%d"
            + " fp=%d protected=%d changed=%d%n",
        typos,
        fixed,
        wrong,
        missed,
        precision,
        recall,
        clean,
        falsePositives,
        protectedCases,
        protectedChanged);
    // Hard guarantees: protected information never changes; no false positive on clean text.
    assertEquals(0, protectedChanged, json);
    assertEquals(0, falsePositives, json);
    assertTrue(precision >= 0.95, json);
  }
}
