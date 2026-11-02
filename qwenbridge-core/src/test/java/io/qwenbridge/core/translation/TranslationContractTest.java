package io.qwenbridge.core.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import io.qwenbridge.core.translation.TranslationResult.SkipReason;
import io.qwenbridge.core.translation.TranslationResult.Skipped;
import io.qwenbridge.core.translation.TranslationResult.Translated;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QB-021. Contract tests run offline against fixtures and must hold for every provider; they never
 * call a model. Translation quality of a real provider is a separate, opt-in measurement.
 */
class TranslationContractTest {

  static final EntityDictionary TERMS =
      new EntityDictionary(
          List.of(
              new EntityDictionary.Term("brand", "brand", "Nike", List.of("Nike", "نایکی")),
              new EntityDictionary.Term("brand", "brand", "Lenovo", List.of("Lenovo")),
              new EntityDictionary.Term(
                  "material", "material", "leather", List.of("leather", "läder", "چرم"))));

  static Stream<Arguments> corpus() throws IOException {
    try (InputStream in =
        TranslationContractTest.class.getResourceAsStream("/translation/preservation-cases.json")) {
      JsonNode rows = new ObjectMapper().readTree(in);
      return StreamSupport.stream(rows.spliterator(), false)
          .map(row -> Arguments.of(row.path("id").asText(), row));
    }
  }

  /** The semantic-preservation corpus, including code-switched and uncovered-language cases. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void preservationCorpus(String id, JsonNode row) {
    PreservationReport report =
        PreservationChecker.check(
            row.path("source").asText(),
            new LanguageTag(row.path("from").asText()),
            row.path("translation").asText(),
            new LanguageTag(row.path("to").asText()),
            TERMS);
    assertEquals(row.path("expected").asText(), report.status().name(), () -> id + " " + report);
    if (row.has("kinds")) {
      Set<String> expected = new TreeSet<>();
      row.path("kinds").forEach(k -> expected.add(k.asText()));
      Set<String> actual =
          report.issues().stream()
              .map(i -> i.kind().name())
              .collect(Collectors.toCollection(TreeSet::new));
      assertEquals(expected, actual, () -> id + " " + report);
    }
  }

  @Nested
  class ProviderConformance {

    static final List<TranslationRequest> PROBES =
        List.of(
            new TranslationRequest(
                "skor under 100 EUR",
                new LanguageTag("sv"),
                new LanguageTag("en"),
                Duration.ofSeconds(1)),
            new TranslationRequest(
                "Nike shoes برای winter",
                new LanguageTag("mul"),
                new LanguageTag("en"),
                Duration.ofSeconds(1)),
            new TranslationRequest(
                "x", new LanguageTag("fa"), new LanguageTag("sv"), Duration.ofSeconds(1)));

    @Test
    void aWellBehavedProviderConforms() {
      TranslationProvider fixture =
          TranslationStageTest.table(
              Map.of("skor under 100 EUR", "shoes under 100 EUR"),
              TranslationCapabilities.anyToAny(1000));
      assertEquals(List.of(), TranslationConformance.verify(fixture, PROBES));
    }

    @Test
    void contractBreachesAreNamed() {
      TranslationProvider broken =
          TranslationStageTest.provider(
              TranslationCapabilities.anyToAny(1000),
              r ->
                  switch (r.source().primaryLanguage()) {
                    case "sv" ->
                        new Translated(
                            "Schuhe",
                            r.source(),
                            new LanguageTag("de"),
                            "other",
                            Confidence.unknown(),
                            Map.of());
                    case "mul" -> new Skipped(SkipReason.SAME_LANGUAGE);
                    default -> throw new IllegalStateException("boom");
                  });
      Set<String> codes =
          TranslationConformance.verify(broken, PROBES).stream()
              .map(TranslationConformance.Violation::code)
              .collect(Collectors.toSet());
      assertEquals(Set.of("WRONG_TARGET", "PROVIDER_ID_MISMATCH", "SKIPPED", "THREW"), codes);
    }

    @Test
    void failuresAreValidResults() {
      TranslationProvider failing =
          TranslationStageTest.provider(
              TranslationCapabilities.anyToAny(1000),
              r -> Failed.of(Failure.PROVIDER_ERROR, "table"));
      assertTrue(TranslationConformance.verify(failing, PROBES).isEmpty());
    }
  }
}
