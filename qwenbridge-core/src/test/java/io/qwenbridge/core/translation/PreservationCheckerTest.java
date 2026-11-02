package io.qwenbridge.core.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.PreservationIssue.Direction;
import io.qwenbridge.core.translation.PreservationIssue.Kind;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** QB-018: numbers, currencies, units, entities, operators and negation survive translation. */
class PreservationCheckerTest {

  static final EntityDictionary TERMS =
      new EntityDictionary(
          List.of(
              new EntityDictionary.Term("brand", "brand", "Nike", List.of("Nike", "نایکی")),
              new EntityDictionary.Term("brand", "brand", "Lenovo", List.of("Lenovo")),
              new EntityDictionary.Term(
                  "material", "material", "leather", List.of("leather", "läder", "چرم"))));

  static LanguageTag tag(String value) {
    return new LanguageTag(value);
  }

  static PreservationReport check(String source, String from, String target, String to) {
    return PreservationChecker.check(source, tag(from), target, tag(to), TERMS);
  }

  static Set<String> problems(PreservationReport report) {
    return report.issues().stream()
        .map(i -> i.kind() + ":" + i.direction())
        .collect(Collectors.toSet());
  }

  @Nested
  class FaithfulTranslations {

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource(
        delimiter = '|',
        value = {
          "Lenovo زیر ۱۵۰۰ یورو | fa | Lenovo under 1500 EUR | en",
          "skor utan läder under 1200 SEK | sv | shoes without leather under 1200 SEK | en",
          "کفش نایکی بدون چرم حداکثر ۲۰۰ یورو | fa | Nike shoes without leather at most 200 euro |"
              + " en",
          "laptop 16 GB under 900 EUR | en | bärbar dator 16 GB under 900 EUR | sv",
          "pris mellan 1,5 och 3 kg | sv | price between 1.5 and 3 kg | en",
          "logs from pod-7f9c2 above 80 % | en | loggar från pod-7f9c2 över 80 % | sv"
        })
    void coveredLanguagePairsArePreserved(String source, String from, String target, String to) {
      PreservationReport report = check(source, from, target, to);
      assertEquals(List.of(), report.issues());
      assertEquals(PreservationReport.Status.PRESERVED, report.status());
    }
  }

  @Nested
  class ChangedMeaning {

    @Test
    void changedNumberAndDroppedBrandAreBothReported() {
      PreservationReport report =
          check("Lenovo زیر ۱۵۰۰ یورو", "fa", "laptop below 1000 EUR", "en");
      assertEquals(PreservationReport.Status.NOT_PRESERVED, report.status());
      assertTrue(problems(report).contains("NUMBER:LOST"));
      assertTrue(problems(report).contains("NUMBER:ADDED"));
      assertTrue(problems(report).contains("ENTITY:LOST"));
      PreservationIssue lostNumber =
          report.lost().stream().filter(i -> i.kind() == Kind.NUMBER).findFirst().orElseThrow();
      assertEquals(List.of("1500"), lostNumber.subjects());
    }

    @Test
    void droppedExclusionIsLostNegation() {
      PreservationReport report =
          check("skor utan läder under 1200 SEK", "sv", "leather shoes under 1200 SEK", "en");
      assertTrue(problems(report).contains("NEGATION:LOST"), report.toString());
    }

    @Test
    void invertedComparisonIsAnOperatorChange() {
      PreservationReport report = check("under 100 EUR", "en", "över 100 EUR", "sv");
      assertTrue(problems(report).contains("OPERATOR:LOST"));
      assertTrue(problems(report).contains("OPERATOR:ADDED"));
    }

    @Test
    void addedExclusionIsReported() {
      PreservationReport report = check("Nike shoes", "en", "inte Nike skor", "sv");
      assertTrue(problems(report).contains("NEGATION:ADDED"));
    }

    @Test
    void inventedCurrencyIsReported() {
      // "کرون" is ambiguous (SEK, NOK, DKK...): the translator must not resolve it.
      PreservationReport report = check("زیر ۱۲۰۰ کرون", "fa", "under 1200 SEK", "en");
      PreservationIssue added =
          report.issues().stream().filter(i -> i.kind() == Kind.CURRENCY).findFirst().orElseThrow();
      assertEquals(Direction.ADDED, added.direction());
      assertEquals(List.of("SEK"), added.subjects());
    }

    @Test
    void changedUnitIsReported() {
      PreservationReport report = check("cable 2 m", "en", "kabel 2 cm", "sv");
      assertTrue(problems(report).contains("UNIT:LOST"));
      assertTrue(problems(report).contains("UNIT:ADDED"));
    }

    @Test
    void identifiersMustSurviveVerbatim() {
      PreservationReport report =
          check("errors on 10.0.0.12 for SKU-4471", "en", "fel på 10.0.0.12 för SKU-4417", "sv");
      PreservationIssue lost =
          report.lost().stream().filter(i -> i.kind() == Kind.IDENTIFIER).findFirst().orElseThrow();
      assertEquals(List.of("SKU-4471"), lost.subjects());
    }
  }

  @Nested
  class Coverage {

    @Test
    void uncoveredLanguagesAreNeverReportedAsPreserved() {
      PreservationReport report =
          check(
              "schwarze Nike Schuhe unter 100 Euro", "de", "black Nike shoes under 100 EUR", "en");
      assertEquals(PreservationReport.Status.PARTIALLY_VERIFIED, report.status());
      assertEquals(
          Set.of(Kind.OPERATOR, Kind.NEGATION, Kind.CURRENCY, Kind.NUMBER, Kind.ENTITY),
          report.unverified());
    }

    @Test
    void lostNumbersAreCaughtInAnyLanguage() {
      PreservationReport report =
          check("schwarze Nike Schuhe unter 100 Euro", "de", "black Nike shoes", "en");
      assertTrue(problems(report).contains("NUMBER:LOST"));
    }

    @Test
    void codeSwitchedSourceIsCheckedForLanguageNeutralKinds() {
      PreservationReport report =
          check(
              "Nike shoes برای winter زیر ۱۰۰ EUR", "mul", "Nike winter shoes under 100 EUR", "en");
      assertTrue(report.preserved(), report.toString());
      assertTrue(report.unverified().contains(Kind.OPERATOR));
    }
  }
}
