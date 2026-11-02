package io.qwenbridge.core.extraction;

import static io.qwenbridge.core.queryir.Constraint.Operator.BETWEEN;
import static io.qwenbridge.core.queryir.Constraint.Operator.EQ;
import static io.qwenbridge.core.queryir.Constraint.Operator.GT;
import static io.qwenbridge.core.queryir.Constraint.Operator.GTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.IN;
import static io.qwenbridge.core.queryir.Constraint.Operator.LT;
import static io.qwenbridge.core.queryir.Constraint.Operator.LTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NOT_IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.queryir.SemanticSignal;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaValidator;
import io.qwenbridge.core.schema.SchemaVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** QB-029–QB-035: grounded extraction across domains and languages. */
class ConstraintExtractorTest {

  static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

  static final DomainSchema COMMERCE =
      new DomainSchema(
          "commerce",
          new SchemaVersion(1, 0),
          Map.of(
              "price",
                  FieldDefinition.of(
                      "price", FieldType.MONEY, Set.of(EQ, LT, LTE, GT, GTE, BETWEEN)),
              "color", FieldDefinition.of("color", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "material",
                  FieldDefinition.of("material", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "brand", FieldDefinition.of("brand", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "size", FieldDefinition.of("size", FieldType.NUMBER, Set.of(EQ, LT, LTE, GT, GTE))),
          Set.of("brand", "material", "color"),
          Set.of("search"));

  static final DomainSchema DEVOPS =
      new DomainSchema(
          "devops",
          new SchemaVersion(1, 0),
          Map.of(
              "memory_usage",
              new FieldDefinition(
                  "memory_usage",
                  FieldType.QUANTITY,
                  Set.of(GT, GTE, LT, LTE),
                  false,
                  Optional.of("%"),
                  Optional.of(BigDecimal.ZERO),
                  Optional.of(new BigDecimal("100")),
                  Set.of()),
              "observed_at",
              FieldDefinition.of("observed_at", FieldType.TEMPORAL, Set.of(GT, GTE, LT, LTE, EQ))),
          Set.of("kubernetes.pod"),
          Set.of("investigate"));

  static final DomainSchema SECURITY =
      new DomainSchema(
          "security",
          new SchemaVersion(1, 0),
          Map.of(
              "bot_score",
              FieldDefinition.of("bot_score", FieldType.NUMBER, Set.of(LT, LTE, GT, GTE)),
              "source.trust",
              new FieldDefinition(
                  "source.trust",
                  FieldType.IDENTIFIER,
                  Set.of(EQ, NE, IN, NOT_IN),
                  false,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Set.of("trusted", "untrusted"))),
          Set.of("network.ip_address"),
          Set.of("analyze"));

  static final EntityDictionary COMMERCE_TERMS =
      new EntityDictionary(
          List.of(
              new EntityDictionary.Term("brand", "brand", "Nike", List.of("Nike", "نایکی")),
              new EntityDictionary.Term(
                  "material", "material", "leather", List.of("leather", "läder", "چرم")),
              new EntityDictionary.Term(
                  "color", "color", "black", List.of("black", "svarta", "svart", "مشکی"))));

  static final EntityDictionary DEVOPS_TERMS =
      new EntityDictionary(
          List.of(
              new EntityDictionary.Term(
                  "kubernetes.pod", "resource", "pod", List.of("pods", "pod"))));

  static ExtractionContext ctx(
      String language,
      String locale,
      DomainSchema schema,
      EntityDictionary dictionary,
      boolean withTime) {
    return new ExtractionContext(
        LanguageTag.parse(language),
        Optional.ofNullable(locale).flatMap(LanguageTag::parse),
        withTime ? Optional.of(NOW) : Optional.empty(),
        withTime ? Optional.of(ZoneId.of("Europe/Stockholm")) : Optional.empty(),
        Optional.ofNullable(schema),
        dictionary);
  }

  /** Every extraction must be a valid QueryIR fragment (spans match, operators fit values). */
  static Extraction extract(String text, ExtractionContext context) {
    Extraction e = ConstraintExtractor.extract(text, context);
    QueryIR ir = e.applyTo(QueryIR.of(text, LanguageTag.UNDETERMINED));
    assertEquals(List.of(), QueryIrValidator.validate(ir), text);
    return e;
  }

  static Constraint only(Extraction e, String field) {
    List<Constraint> found = e.constraints().stream().filter(c -> c.field().equals(field)).toList();
    assertEquals(1, found.size(), () -> field + " in " + e.constraints());
    return found.getFirst();
  }

  // --- Scenario A (Persian commerce) -------------------------------------------------------

  @Test
  void persianCommerceKeepsKronaUnresolvedAndPersianDigitsExact() {
    String text = "کفش مشکی برای پیاده‌روی زیر ۱۲۰۰ کرون می‌خوام.";
    Extraction e = extract(text, ctx("fa", "fa-IR", COMMERCE, COMMERCE_TERMS, true));

    Constraint price = only(e, "price");
    assertEquals(LT, price.operator());
    IrValue.Money money = assertInstanceOf(IrValue.Money.class, price.value().orElseThrow());
    assertEquals(new BigDecimal("1200"), money.amount());
    assertTrue(money.currency().isEmpty(), "kronor is ambiguous in a Persian/Iranian context");
    assertTrue(
        e.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.AMBIGUOUS_CURRENCY));
    assertEquals(Strength.HARD, price.strength());
    assertTrue(price.reason().contains("زیر"));

    assertTrue(
        e.entities().stream()
            .anyMatch(
                en ->
                    en.type().equals("color") && en.normalizedValue().orElse("").equals("black")));
    assertTrue(
        e.semanticSignals().stream().anyMatch(s -> s.concept().equals("suitability")),
        "for walking -> suitability signal");
  }

  @Test
  void swedishContextResolvesKronorToSek() {
    Extraction e =
        extract(
            "svarta löparskor under 1200 kronor",
            ctx("sv", "sv-SE", COMMERCE, COMMERCE_TERMS, false));
    IrValue.Money money = (IrValue.Money) only(e, "price").value().orElseThrow();
    assertEquals(Optional.of("SEK"), money.currency());
    assertTrue(
        e.unresolved().stream().noneMatch(u -> u.kind() == Unresolved.Kind.AMBIGUOUS_CURRENCY));
  }

  // --- Scenario B (mixed language, negation) -----------------------------------------------

  @Test
  void mixedLanguageNegationIsPreservedAsANegativeConstraint() {
    String text = "Nike shoes برای winter, not leather.";
    Extraction e = extract(text, ctx("en", null, COMMERCE, COMMERCE_TERMS, false));
    Constraint material = only(e, "material");
    assertEquals(NE, material.operator());
    assertEquals("leather", ((IrValue.Text) material.value().orElseThrow()).value());
    assertEquals("not leather", material.span().orElseThrow().text());
    assertTrue(e.entities().stream().anyMatch(en -> en.type().equals("brand")));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "shoes without leather | en | leather",
        "skor utan läder | sv | leather",
        "skor, inte läder | sv | leather",
        "کفش بدون چرم | fa | leather",
        // Regression: a following comparison must not swallow the exclusion.
        "shoes without leather under 1200 SEK | en | leather",
        "skor utan läder under 1200 SEK | sv | leather",
        "کفش بدون چرم زیر ۱۲۰۰ کرون | fa | leather"
      })
  void negationWorksInEveryLanguage(String text, String language, String excluded) {
    Constraint material =
        only(extract(text, ctx(language, null, COMMERCE, COMMERCE_TERMS, false)), "material");
    assertEquals(NE, material.operator());
    assertEquals(excluded, ((IrValue.Text) material.value().orElseThrow()).value());
    assertTrue(material.negative());
  }

  @Test
  void unknownNegationTargetsBecomeFreeTextExclusionsNotPreferences() {
    Extraction e =
        extract(
            "hotels not near the airport", ctx("en", null, null, EntityDictionary.EMPTY, false));
    Constraint c = only(e, "content");
    assertEquals(Operator.NOT_CONTAINS, c.operator());
    assertTrue(c.negative());
    assertTrue(c.reason().contains("free text"));
  }

  // --- Scenario C (DevOps) -----------------------------------------------------------------

  @Test
  void devopsThresholdAndWindowBindToSchemaFields() {
    String text = "Find Kubernetes pods with memory usage above 85% during the last hour";
    Extraction e = extract(text, ctx("en", null, DEVOPS, DEVOPS_TERMS, true));

    Constraint memory = only(e, "memory_usage");
    assertEquals(GT, memory.operator());
    assertEquals(new IrValue.Quantity(new BigDecimal("85"), "%"), memory.value().orElseThrow());
    assertTrue(memory.reason().contains("above"));

    Constraint window = only(e, "observed_at");
    IrValue.Temporal t = (IrValue.Temporal) window.value().orElseThrow();
    assertEquals(IrValue.Temporal.Kind.RELATIVE_PAST, t.kind());
    assertEquals("PT1H", t.value());
    assertEquals(Optional.of(NOW.toString()), t.reference());
    assertTrue(e.entities().stream().anyMatch(en -> en.type().equals("kubernetes.pod")));
    assertEquals(
        List.of(),
        SchemaValidator.validate(e.applyTo(QueryIR.of(text, LanguageTag.UNDETERMINED)), DEVOPS)
            .stream()
            .filter(
                v -> v.code() != io.qwenbridge.core.schema.SchemaViolation.Code.UNKNOWN_ENTITY_TYPE)
            .toList());
  }

  @Test
  void relativeWindowWithoutReferenceTimeStaysUnresolved() {
    Extraction e =
        extract(
            "errors in the past 24 hours", ctx("en", null, DEVOPS, EntityDictionary.EMPTY, false));
    IrValue.Temporal t = (IrValue.Temporal) only(e, "observed_at").value().orElseThrow();
    assertEquals("PT24H", t.value());
    assertFalse(t.resolved());
    assertTrue(
        e.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.MISSING_REFERENCE_TIME));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "fel under de senaste 24 timmarna | sv | PT24H",
        "خطاهای ۷ روز گذشته | fa | PT168H",
        "latency over the last 15 minutes | en | PT15M",
        "logs in the past 2 weeks | en | PT336H"
      })
  void relativeWindowsInEveryLanguage(String text, String language, String duration) {
    Extraction e = extract(text, ctx(language, null, DEVOPS, EntityDictionary.EMPTY, true));
    assertEquals(
        duration, ((IrValue.Temporal) only(e, "observed_at").value().orElseThrow()).value());
  }

  @Test
  void calendarDaysNeedATimeZone() {
    Extraction without =
        extract("failed logins yesterday", ctx("en", null, DEVOPS, EntityDictionary.EMPTY, false));
    assertTrue(without.constraints().isEmpty());
    assertTrue(
        without.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.MISSING_TIME_ZONE));
    Extraction with =
        extract("failed logins yesterday", ctx("en", null, DEVOPS, EntityDictionary.EMPTY, true));
    assertEquals(
        "2026-10-30", ((IrValue.Temporal) only(with, "observed_at").value().orElseThrow()).value());
  }

  // --- Scenario D (Security) ----------------------------------------------------------------

  @Test
  void securityThresholdAndExclusionUseTheSchemaVocabulary() {
    String text = "Identify IPs with bot score below 10, excluding trusted sources";
    Extraction e = extract(text, ctx("en", null, SECURITY, EntityDictionary.EMPTY, false));
    Constraint score = only(e, "bot_score");
    assertEquals(LT, score.operator());
    assertEquals(new IrValue.Number(BigDecimal.TEN), score.value().orElseThrow());
    Constraint trust = only(e, "source.trust");
    assertEquals(NE, trust.operator());
    assertEquals(new IrValue.Text("trusted"), trust.value().orElseThrow());
  }

  @Test
  void identifiersAreExactEntities() {
    Extraction e =
        extract(
            "Block 192.168.10.25 and order SKU-12345",
            ctx("en", null, null, EntityDictionary.EMPTY, false));
    assertTrue(
        e.entities().stream()
            .anyMatch(
                en ->
                    en.type().equals("network.ip_address") && en.value().equals("192.168.10.25")));
    assertTrue(
        e.entities().stream()
            .anyMatch(en -> en.type().equals("identifier.code") && en.value().equals("SKU-12345")));
    assertTrue(e.constraints().isEmpty(), "identifiers alone are not constraints");
  }

  // --- Numbers, ranges, units, currencies (QB-030) ---------------------------------------

  @Test
  void rangesShareTheTrailingCurrencyAndStayExact() {
    Extraction e =
        extract(
            "laptops between 899.50 and 1500 EUR",
            ctx("en", null, COMMERCE, EntityDictionary.EMPTY, false));
    IrValue.Range range = (IrValue.Range) only(e, "price").value().orElseThrow();
    assertEquals(
        new IrValue.Money(new BigDecimal("899.50"), Optional.of("EUR")), range.min().orElseThrow());
    assertEquals(
        new IrValue.Money(new BigDecimal("1500"), Optional.of("EUR")), range.max().orElseThrow());
    assertEquals(BETWEEN, only(e, "price").operator());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "up to 500 USD | en | LTE | 500",
        "at least 16 GB ram | en | GTE | 16",
        "minst 16 GB | sv | GTE | 16",
        "högst 1,5 kg | sv | LTE | 1.5",
        "حداقل ۸ گیگ | fa | GTE | 8",
        "no more than 2.5 kg | en | LTE | 2.5"
      })
  void comparisonCuesAndDecimalConventions(
      String text, String language, Operator op, String amount) {
    Extraction e = extract(text, ctx(language, null, null, EntityDictionary.EMPTY, false));
    assertEquals(1, e.constraints().size(), () -> e.constraints().toString());
    Constraint c = e.constraints().getFirst();
    assertEquals(op, c.operator());
    BigDecimal value =
        switch (c.value().orElseThrow()) {
          case IrValue.Money m -> m.amount();
          case IrValue.Quantity q -> q.value();
          case IrValue.Number n -> n.value();
          default -> null;
        };
    assertEquals(0, new BigDecimal(amount).compareTo(value), text);
    assertTrue(
        e.constraints().stream().noneMatch(Constraint::negative),
        "no more than is a bound, not an exclusion");
  }

  @Test
  void ambiguousSeparatorsAreReportedAndDollarIsNotAssumedToBeUsd() {
    Extraction e =
        extract("under 1.200 dollars", ctx("en", null, null, EntityDictionary.EMPTY, false));
    IrValue.Money money = (IrValue.Money) e.constraints().getFirst().value().orElseThrow();
    assertTrue(money.currency().isEmpty());
    assertTrue(e.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.AMBIGUOUS_NUMBER));
    assertTrue(
        e.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.AMBIGUOUS_CURRENCY));
    Extraction us =
        extract("under 1200 dollars", ctx("en", "en-US", null, EntityDictionary.EMPTY, false));
    assertEquals(
        Optional.of("USD"),
        ((IrValue.Money) us.constraints().getFirst().value().orElseThrow()).currency());
  }

  @Test
  void bareNumbersAreNeverInventedIntoConstraints() {
    Extraction e =
        extract("iphone 15 pro 256", ctx("en", null, COMMERCE, EntityDictionary.EMPTY, false));
    assertTrue(e.constraints().isEmpty(), () -> e.constraints().toString());
  }

  @Test
  void unknownWordsAfterANumberAreNotPartOfTheConstraintSpan() {
    Extraction e =
        extract("rooms for under 100 people", ctx("en", null, null, EntityDictionary.EMPTY, false));
    assertEquals("under 100", e.constraints().getFirst().span().orElseThrow().text());
  }

  // --- Field binding without a schema ------------------------------------------------------

  @Test
  void withoutASchemaFieldsAreProposalsAndReported() {
    Extraction e =
        extract(
            "black running shoes under 1200 SEK",
            ctx("en", null, null, EntityDictionary.EMPTY, false));
    assertEquals("amount", e.constraints().getFirst().field(), "a nearby noun is not a field");
    assertTrue(e.unresolved().stream().anyMatch(u -> u.kind() == Unresolved.Kind.UNBOUND_FIELD));
    Extraction score =
        extract("bot score below 10", ctx("en", null, null, EntityDictionary.EMPTY, false));
    assertEquals("bot_score", score.constraints().getFirst().field());
  }

  // --- Hard and soft (QB-032) ---------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "preferably under 100 EUR | en | SOFT",
        "helst under 100 kr | sv | SOFT",
        "around 50 kg | en | SOFT",
        "ترجیحاً زیر ۱۰۰ یورو | fa | SOFT",
        "under 100 EUR | en | HARD"
      })
  void preferenceWordingSoftensConstraintsAndTheReasonIsKept(
      String text, String language, Strength strength) {
    Constraint c =
        extract(text, ctx(language, null, null, EntityDictionary.EMPTY, false))
            .constraints()
            .getFirst();
    assertEquals(strength, c.strength());
    assertFalse(c.reason().isBlank());
  }

  // --- Subjective attributes (QB-034) ------------------------------------------------------

  @Test
  void subjectiveWordsAreSignalsNotFilters() {
    Extraction e =
        extract(
            "comfortable lightweight shoes, not too heavy",
            ctx("en", null, COMMERCE, EntityDictionary.EMPTY, false));
    assertTrue(
        e.semanticSignals().stream()
            .map(SemanticSignal::concept)
            .toList()
            .containsAll(List.of("comfort", "lightweight")));
    assertTrue(
        e.constraints().stream()
            .noneMatch(c -> c.field().equals("comfort") || c.field().equals("lightweight")));
  }

  @Test
  void negatedSubjectiveWordHasNegativePolarity() {
    Extraction e =
        extract(
            "pods that are not suspicious", ctx("en", null, null, EntityDictionary.EMPTY, false));
    SemanticSignal s = e.semanticSignals().getFirst();
    assertEquals("suspiciousness", s.concept());
    assertEquals(SemanticSignal.Polarity.NEGATIVE, s.polarity());
  }

  // --- Geography (QB-031) -------------------------------------------------------------------

  @Test
  void countriesAreRecognisedInThreeLanguagesWithIsoCodes() {
    assertEquals(Optional.of("SE"), countryCode("hotels in Sweden"));
    assertEquals(Optional.of("SE"), countryCode("hotell i Sverige"));
    assertEquals(Optional.of("IR"), countryCode("هتل در ایران"));
    assertEquals(
        Optional.empty(), countryCode("roast turkey recipe"), "common words are not countries");
  }

  private static Optional<String> countryCode(String text) {
    return extract(text, ExtractionContext.none()).entities().stream()
        .filter(en -> en.type().equals("location.country"))
        .findFirst()
        .flatMap(en -> en.normalizedValue());
  }

  // --- Numerals -------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0} (commaDecimal={1}) -> {2}")
  @CsvSource({
    "1200, false, 1200, false",
    "'1,200', false, 1200, false",
    "'1,200.50', false, 1200.50, false",
    "'1.200,50', true, 1200.50, false",
    "'1,5', true, 1.5, false",
    "1.200, false, 1.200, true",
    "۱۲۰۰, false, 1200, false",
    "۱٫۵, false, 1.5, false",
    "१२, false, 12, false"
  })
  void numeralsParseExactlyInAnyDigitScript(
      String token, boolean commaDecimal, String expected, boolean ambiguous) {
    Numerals.Parsed parsed = Numerals.parse(token, commaDecimal).orElseThrow();
    assertEquals(new BigDecimal(expected), parsed.value());
    assertEquals(ambiguous, parsed.ambiguous());
  }
}
