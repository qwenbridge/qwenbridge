package io.qwenbridge.core.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.GlossaryTranslationProvider;
import io.qwenbridge.core.translation.PreservationReport;
import io.qwenbridge.core.translation.TranslationProvider;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real local glossary provider with real language-pair fixtures (QB-286 actual provider tests).
 * The fixtures are small hand-written phrase tables (en→sv, en→fa, en→nb, en→nn, sv→en): these
 * tests show the provider and the preservation checks on real words, not general translation
 * quality.
 */
class GlossaryExpansionTest {

  static final Path GLOSSARY = Path.of("src/test/resources/expansion/glossary");

  static TranslationProvider glossary() throws Exception {
    return GlossaryTranslationProvider.load("glossary", GLOSSARY);
  }

  static ExpansionResult expand(String query, String source, String... targets) throws Exception {
    return QueryExpander.expand(
        new QueryExpander.Request(
            query,
            query,
            SourceLanguage.of(new LanguageTag(source)),
            new ExpansionSettings(
                true, List.of(targets), ExpansionPolicy.ALL_TARGETS, Map.of(), 0.5),
            null),
        List.of(glossary()),
        new ExpansionLimits(Duration.ofSeconds(2), Duration.ofSeconds(5), 4),
        Optional.empty(),
        () -> false);
  }

  static String text(ExpansionResult r, int i) {
    return r.targets().get(i).text().orElseThrow(() -> new AssertionError(r.targets().get(i)));
  }

  @Test
  void englishQueryToFiveConfiguredLanguages() throws Exception {
    var r =
        expand("I need a red winter jacket under 100 euros", "en", "en", "fa", "sv", "nb", "nn");
    assertEquals(
        List.of("UNCHANGED", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS"),
        r.targets().stream().map(t -> t.status().name()).toList(),
        r.targets().toString());
    assertEquals("I need a red winter jacket under 100 euros", text(r, 0));
    assertEquals("من نیاز دارم به یک ژاکت زمستانی قرمز زیر 100 یورو", text(r, 1));
    assertEquals("jag behöver en röd vinterjacka under 100 euro", text(r, 2));
    assertEquals("jeg trenger en rød vinterjakke under 100 euro", text(r, 3));
    assertEquals("eg treng ein raud vinterjakke under 100 euro", text(r, 4));
    assertEquals(ExpansionResult.Status.COMPLETE, r.status());
    assertEquals(5, r.retrievalTexts().size());
    // en, sv and fa cues are known: every kind is verified. nb and nn are not in the cue
    // lexicon, so operators, negation and currency are reported unverified, never preserved.
    assertEquals(
        PreservationReport.Status.PRESERVED,
        r.targets().get(1).preservation().orElseThrow().status());
    assertEquals(
        PreservationReport.Status.PRESERVED,
        r.targets().get(2).preservation().orElseThrow().status());
    assertEquals(
        PreservationReport.Status.PARTIALLY_VERIFIED,
        r.targets().get(3).preservation().orElseThrow().status());
  }

  @Test
  void swedishQueryIsTranslatedBackAndKeptForSwedish() throws Exception {
    var r = expand("jag behöver en röd vinterjacka under 100 euro", "sv", "en", "sv", "fa");
    assertEquals("i need a red winter jacket under 100 euros", text(r, 0));
    assertEquals(TargetStatus.UNCHANGED, r.targets().get(1).status());
    assertEquals(
        TargetStatus.UNSUPPORTED_LANGUAGE_PAIR, r.targets().get(2).status(), "no sv>fa table");
    assertEquals(ExpansionResult.Status.PARTIAL, r.status());
  }

  @Test
  void negationIsTranslatedAndVerified() throws Exception {
    var r = expand("jacket without leather", "en", "sv", "fa");
    assertEquals("jacka utan läder", text(r, 0));
    assertEquals("ژاکت بدون چرم", text(r, 1));
    assertEquals(
        PreservationReport.Status.PRESERVED,
        r.targets().get(0).preservation().orElseThrow().status());
  }

  @Test
  void technicalQueryKeepsIdentifiersAndNumbers() throws Exception {
    var r = expand("show errors from redis03-pt1 in the last 30 minutes", "en", "sv");
    assertEquals("visa fel från redis03-pt1 de senaste 30 minuterna", text(r, 0));
    assertTrue(r.targets().getFirst().identifiersPreserved());
  }

  @Test
  void unknownWordsLowerCoverageAndConfidence() throws Exception {
    var r = expand("blue umbrella with wooden handle jacket", "en", "sv");
    assertEquals(TargetStatus.LOW_CONFIDENCE, r.targets().getFirst().status());
    assertTrue(r.targets().getFirst().reason().orElseThrow().startsWith("coverage"));
    var none = expand("blue umbrella", "en", "sv");
    assertEquals(
        TargetStatus.FAILED,
        none.targets().getFirst().status(),
        "nothing translated is not a translation");
    assertEquals("INVALID_OUTPUT", none.targets().getFirst().reason().orElseThrow());
  }

  @Test
  void declaresItsPairsAndRefusesMalformedTables(@TempDir Path dir) throws Exception {
    var p = glossary();
    assertTrue(p.capabilities().supportsPair(new LanguageTag("en"), new LanguageTag("fa")));
    assertTrue(!p.capabilities().supportsPair(new LanguageTag("sv"), new LanguageTag("fa")));
    assertEquals("5", p.metadata().get("pairs"));
    Files.writeString(dir.resolve("english-swedish.tsv"), "a\tb\n");
    assertThrows(IllegalArgumentException.class, () -> GlossaryTranslationProvider.load("g", dir));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GlossaryTranslationProvider.of(
                "g", Map.of("en>sv", new StringReader("no tab here\n"))));
    Path empty = Files.createDirectory(dir.resolve("empty"));
    assertThrows(
        IllegalArgumentException.class, () -> GlossaryTranslationProvider.load("g", empty));
  }

  static ExpansionResult expandWith(String query, SourceLanguage source, String... targets)
      throws Exception {
    return QueryExpander.expand(
        new QueryExpander.Request(
            query,
            query,
            source,
            new ExpansionSettings(
                true, List.of(targets), ExpansionPolicy.ALL_TARGETS, Map.of(), 0.5),
            null),
        List.of(glossary()),
        new ExpansionLimits(Duration.ofSeconds(2), Duration.ofSeconds(5), 4),
        Optional.empty(),
        () -> false);
  }

  @Test
  void providerVocabularyOverrulesAStatisticalMisdetection() throws Exception {
    // Measured in this repository: the low-accuracy detector labels this English query Swedish.
    var misdetected =
        new SourceLanguage(
            Optional.of(new LanguageTag("sv")),
            true,
            false,
            List.of(),
            io.qwenbridge.core.confidence.Confidence.modelDecision());
    var r = expandWith("I need a red winter jacket under 100 euros", misdetected, "en", "sv");
    assertEquals("en", r.source().tag().value());
    assertEquals(SourceLanguage.Basis.VOCABULARY, r.source().basis());
    assertEquals(TargetStatus.UNCHANGED, r.targets().get(0).status());
    assertEquals("jag behöver en röd vinterjacka under 100 euro", text(r, 1));
    assertTrue(r.explanations().getFirst().contains("said sv"), r.explanations().toString());

    var undetermined =
        expandWith(
            "show errors from redis03-pt1 in the last 30 minutes",
            SourceLanguage.undetermined(),
            "sv");
    assertEquals(SourceLanguage.Basis.VOCABULARY, undetermined.source().basis());
    assertEquals(TargetStatus.SUCCESS, undetermined.targets().getFirst().status());
  }

  @Test
  void scriptBasedAndDeclaredLanguagesAreNotOverruled() throws Exception {
    var r =
        expandWith(
            "I need a red winter jacket under 100 euros",
            SourceLanguage.of(new LanguageTag("sv")),
            "en");
    assertEquals("sv", r.source().tag().value(), "a heuristic detection is kept");
    assertTrue(r.explanations().isEmpty());
    var declared =
        expandWith(
            "I need a red winter jacket", SourceLanguage.declared(new LanguageTag("sv")), "en");
    assertEquals(SourceLanguage.Basis.DECLARED, declared.source().basis());
    var unknownWords = expandWith("qwzx plorf vintage", SourceLanguage.undetermined(), "sv");
    assertEquals(
        TargetStatus.SOURCE_UNDETERMINED,
        unknownWords.targets().getFirst().status(),
        "no evidence: nothing is guessed");
  }
}
