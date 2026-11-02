package io.qwenbridge.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.queryir.RetrievalHints.Mode;
import io.qwenbridge.core.queryir.SubQuery;
import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** QB-022–QB-028: deterministic intent understanding across languages and domains. */
class IntentAnalyzerTest {

  private static final Set<String> DOMAINS = Set.of("commerce", "devops", "security");
  private final IntentAnalyzer analyzer = new IntentAnalyzer();

  private IntentUnderstanding analyze(String text, String language) {
    return analyzer.analyze(text, LanguageTag.parse(language), DOMAINS, List.of());
  }

  private static List<String> types(IntentUnderstanding u) {
    return u.intents().stream().map(Intent::type).toList();
  }

  /** Every result must be a valid QueryIR fragment with spans matching the original text. */
  private static void assertValidIr(String text, IntentUnderstanding u) {
    QueryIR ir = u.applyTo(QueryIR.of(text, LanguageTag.UNDETERMINED));
    assertEquals(List.of(), QueryIrValidator.validate(ir), text);
  }

  // --- QB-022 taxonomy -----------------------------------------------------------------

  @Test
  void taxonomyIsVersionedDomainNeutralAndExtensible() {
    IntentTaxonomy core = IntentTaxonomy.core();
    assertEquals("1.0", IntentTaxonomy.VERSION);
    assertEquals(
        Set.of("search", "compare", "analyze", "explain", "investigate", "refine", "navigate"),
        core.coreIntents().keySet());
    core.coreIntents()
        .keySet()
        .forEach(t -> assertFalse(t.contains("product") || t.contains("pod"), t));

    IntentTaxonomy extended = core.withExtension("commerce", "reorder", IntentTaxonomy.REFINE);
    assertTrue(extended.isKnown("commerce.reorder"));
    assertEquals(Optional.of("refine"), extended.coreOf("commerce.reorder"));
    assertFalse(core.isKnown("commerce.reorder"), "taxonomies are immutable");
    assertThrows(IllegalArgumentException.class, () -> core.withExtension("commerce", "x", "buy"));
    assertThrows(
        IllegalArgumentException.class,
        () -> extended.withExtension("commerce", "reorder", "refine"));
  }

  @Test
  void legacyIntentNamesMapIntoTheTaxonomy() {
    assertEquals(Optional.of("search"), IntentTaxonomy.fromLegacy("PRODUCT_SEARCH"));
    assertEquals(Optional.of("refine"), IntentTaxonomy.fromLegacy("FILTER"));
    assertEquals(Optional.of("navigate"), IntentTaxonomy.fromLegacy("NAVIGATION"));
    assertEquals(Optional.empty(), IntentTaxonomy.fromLegacy("UNKNOWN"));
  }

  // --- QB-023 single and multiple intents ------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "Find Kubernetes pods with memory usage above 85% during the last hour | en |"
            + " investigate=no;search",
        "Identify IPs with bot score below 10, excluding trusted sources | en | analyze",
        "Why did P99 latency increase during the last hour? | en | explain",
        "Troubleshoot the checkout service errors | en | investigate",
        "Compare the XPS 13 and the MacBook Air | en | compare",
        "Hitta svarta löparskor under 1200 kr | sv | search",
        "Jämför två bärbara datorer | sv | compare",
        "کفش مشکی می‌خوام | fa | search",
        "مقایسه دو گوشی | fa | compare",
        "Go to my orders | en | navigate"
      })
  void intentsAreDetectedWithSpans(String text, String language, String expected) {
    IntentUnderstanding u = analyze(text, language);
    String last = expected.substring(expected.lastIndexOf(';') + 1);
    assertTrue(
        types(u).contains(last.contains("=") ? last.substring(0, last.indexOf('=')) : last),
        types(u) + " for " + text);
    u.intents().forEach(i -> assertEquals(Confidence.Kind.HEURISTIC, i.confidence().kind()));
    assertValidIr(text, u);
  }

  @Test
  void multipleIntentsAreKeptInTextOrder() {
    String text = "Find laptops under 1500 EUR and compare them with tablets";
    IntentUnderstanding u = analyze(text, "en");
    assertEquals(List.of("search", "compare"), types(u));
    assertEquals("Find", u.intents().get(0).span().orElseThrow().text());
    assertEquals("compare", u.intents().get(1).span().orElseThrow().text());
    assertValidIr(text, u);
  }

  @Test
  void contentWithoutACueIsAnImplicitSearchNotASilentDefault() {
    String text = "black running shoes size 42";
    IntentUnderstanding u = analyze(text, "en");
    assertEquals(List.of("search"), types(u));
    assertEquals(
        text,
        u.intents().getFirst().span().orElseThrow().text(),
        "implicit intent cites the whole request");
    assertEquals(QueryAssessment.Status.CLEAR, u.assessment().status());
  }

  @Test
  void longestCueWinsAndCuesNeedWordBoundaries() {
    IntentUnderstanding u = analyze("show me the findings of the audit", "en");
    assertEquals(List.of("search"), types(u));
    assertEquals("show me", u.intents().getFirst().span().orElseThrow().text());
  }

  // --- QB-024 retrieval needs ----------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "Find order SKU-12345 | EXACT;LEXICAL",
        "Block traffic from 192.168.10.25 | EXACT;LEXICAL",
        "black running shoes under 1200 SEK | LEXICAL;STRUCTURED",
        "pods with memory usage above 85% | LEXICAL;STRUCTURED",
        "comfortable shoes for long walks | LEXICAL;SEMANTIC",
        "Nike winter shoes under 1500 SEK, not leather, something lightweight |"
            + " LEXICAL;SEMANTIC;STRUCTURED",
        "کفش راحت برای پیاده‌روی | LEXICAL;SEMANTIC"
      })
  void retrievalNeedsOverlapAndNameNoBackend(String text, String expected) {
    Set<Mode> modes = analyze(text, "und").retrievalHints().modes();
    Set<Mode> wanted =
        java.util.Arrays.stream(expected.split(";"))
            .map(Mode::valueOf)
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(wanted, modes, text);
  }

  @Test
  void hybridIsRecognisedWhenLexicalAndSemanticAreBothUseful() {
    assertTrue(analyze("elegant black dress for a winter wedding", "en").retrievalHints().hybrid());
    assertFalse(analyze("SKU-12345", "en").retrievalHints().hybrid());
  }

  // --- QB-025 decomposition ------------------------------------------------------------

  @Test
  void parallelItemsWithASharedTailBecomeIndependentSubqueries() {
    String text = "Find a laptop and a wireless mouse, both suitable for travel";
    IntentUnderstanding u = analyze(text, "en");
    List<SubQuery> subqueries = u.subqueries();
    assertEquals(2, subqueries.size());
    assertEquals("Find a laptop", subqueries.get(0).objective());
    assertEquals("a wireless mouse", subqueries.get(1).objective());
    subqueries.forEach(
        s -> {
          assertTrue(s.dependsOn().isEmpty());
          assertTrue(s.inheritsParentConstraints(), "the shared tail applies to every item");
          assertEquals(List.of("i1"), s.intentIds(), "the leading intent applies to all items");
        });
    assertValidIr(text, u);
  }

  @Test
  void sequentialStepsDependOnThePreviousStep() {
    String text = "Find pods with memory usage above 85% and then show their logs";
    IntentUnderstanding u = analyze(text, "en");
    assertEquals(2, u.subqueries().size());
    assertEquals(List.of(), u.subqueries().get(0).dependsOn());
    assertEquals(List.of("s1"), u.subqueries().get(1).dependsOn());
    assertEquals(List.of("i2"), u.subqueries().get(1).intentIds());
    assertValidIr(text, u);
  }

  @Test
  void swedishAndPersianDecomposition() {
    IntentUnderstanding sv = analyze("Hitta en laptop och en trådlös mus", "sv");
    assertEquals(2, sv.subqueries().size());
    IntentUnderstanding fa = analyze("یک لپ‌تاپ و یک ماوس بی‌سیم می‌خوام", "fa");
    assertEquals(2, fa.subqueries().size());
  }

  @Test
  void ordinaryConjunctionsAreNotSplit() {
    assertTrue(analyze("black and white shoes", "en").subqueries().isEmpty());
    assertTrue(analyze("rock and roll records", "en").subqueries().isEmpty());
  }

  // --- QB-026, QB-027 ambiguity, out-of-domain, clarification ---------------------------

  @Test
  void vagueRequestsNeedFocusedClarificationInsteadOfFabricatedDetail() {
    IntentUnderstanding u = analyze("Find the best option", "en");
    assertEquals(QueryAssessment.Status.NEEDS_CLARIFICATION, u.assessment().status());
    assertEquals(List.of(QueryAssessment.Issue.NO_SUBJECT), u.assessment().issues());
    assertEquals(1, u.assessment().questions().size());
    assertTrue(u.retrievalHints().modes().isEmpty(), "nothing to retrieve yet");
    assertTrue(analyze("بهترین گزینه", "fa").assessment().questions().getFirst().contains("دنبال"));
    assertTrue(
        analyze("Visa det bästa alternativet", "sv")
            .assessment()
            .questions()
            .getFirst()
            .startsWith("Vad letar du"));
  }

  @Test
  void incompletePreferencesDoNotTriggerClarification() {
    assertTrue(
        analyze("Find running shoes", "en").assessment().executable(),
        "missing size or price is not blocking");
    assertTrue(analyze("pods", "en").assessment().executable());
  }

  @Test
  void hintedDomainThatIsNotEnabledIsOutOfDomainNotAmbiguous() {
    IntentUnderstanding u =
        analyzer.analyze(
            "Find flights to Lisbon", LanguageTag.parse("en"), DOMAINS, List.of("travel"));
    assertEquals(QueryAssessment.Status.OUT_OF_DOMAIN, u.assessment().status());
    assertTrue(u.assessment().questions().getFirst().contains("commerce, devops, security"));
    IntentUnderstanding inDomain =
        analyzer.analyze(
            "Find pods", LanguageTag.parse("en"), DOMAINS, List.of("travel", "devops"));
    assertTrue(inDomain.assessment().executable(), "one enabled hinted domain is enough");
  }

  @Test
  void emptyOrSymbolOnlyTextIsHandled() {
    IntentUnderstanding u = analyze("!!! ???", "und");
    assertTrue(u.intents().isEmpty());
    assertEquals(QueryAssessment.Status.NEEDS_CLARIFICATION, u.assessment().status());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QueryAssessment(
                QueryAssessment.Status.CLEAR,
                List.of(QueryAssessment.Issue.NO_SUBJECT),
                List.of()));
  }
}
