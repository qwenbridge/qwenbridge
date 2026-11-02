package io.qwenbridge.core.validation;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.Constraint.Provenance;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.IrVersion;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.SemanticSignal;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.core.validation.ConstraintAssessment.Status;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConstraintValidatorTest {

  /** A commerce schema that stores price as a plain number and the currency separately. */
  static final DomainSchema COMMERCE =
      new DomainSchema(
          "commerce",
          new SchemaVersion(1, 0),
          Map.of(
              "price",
              FieldDefinition.of("price", FieldType.NUMBER, Set.of(EQ, LT, LTE, GT, GTE, BETWEEN)),
              "currency",
              new FieldDefinition(
                  "currency",
                  FieldType.IDENTIFIER,
                  Set.of(EQ),
                  false,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Set.of("SEK", "EUR", "USD")),
              "color",
              FieldDefinition.of("color", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "weight",
              new FieldDefinition(
                  "weight",
                  FieldType.QUANTITY,
                  Set.of(LTE, GTE),
                  false,
                  Optional.of("kg"),
                  Optional.empty(),
                  Optional.empty(),
                  Set.of())),
          Set.of("brand"),
          Set.of("search"));

  static final DomainMapping COMMERCE_MAPPING =
      new DomainMapping(
          "commerce",
          List.of(
              new MappingRule.FieldAlias("amount-is-price", "amount", "price"),
              new MappingRule.MoneyToNumberAndCurrency("price-money", "price", "currency"),
              new MappingRule.SignalToSoftConstraint(
                  "lightweight",
                  "lightweight",
                  SemanticSignal.Polarity.POSITIVE,
                  "weight",
                  LTE,
                  new IrValue.Quantity(BigDecimal.ONE, "kg"))));

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
                  Set.of())),
          Set.of("service"),
          Set.of("investigate"));

  @Nested
  class SemanticToStructuredMapping {

    @Test
    void moneyBecomesNumberPlusCurrencyThroughApprovedRules() {
      QueryIR ir =
          ir("shoes under 1200 SEK", c("c1", "amount", LT, money("1200", "SEK"), "under 1200 SEK"));

      ValidatedQuery q = valid(ir, COMMERCE, COMMERCE_MAPPING);

      assertEquals(List.of("price", "currency"), fields(q.filters()));
      Constraint price = q.filters().get(0);
      assertEquals(LT, price.operator());
      assertEquals(Optional.of(new IrValue.Number(new BigDecimal("1200"))), price.value());
      assertTrue(price.reason().contains("amount-is-price"), price.reason());
      assertTrue(price.reason().contains("price-money"), price.reason());
      assertEquals(ir.constraints().getFirst().span(), price.span());
      Constraint currency = q.filters().get(1);
      assertEquals(EQ, currency.operator());
      assertEquals(Optional.of(new IrValue.Text("SEK")), currency.value());
      assertTrue(currency.reason().contains("currency of c1"), currency.reason());
    }

    @Test
    void unknownCurrencyIsNeverFilledIn() {
      QueryIR ir =
          ir(
              "under 1200 kronor",
              c(
                  "c1",
                  "price",
                  LT,
                  new IrValue.Money(new BigDecimal("1200"), Optional.empty()),
                  "under 1200"));

      assertEquals(List.of("price"), fields(valid(ir, COMMERCE, COMMERCE_MAPPING).filters()));
    }

    @Test
    void moneyOnAnUnknownFieldIsNotRedirectedWithoutAnAlias() {
      QueryIR ir =
          ir(
              "secret under 5 SEK",
              c("c1", "internal_secret", LT, money("5", "SEK"), "under 5 SEK"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(report.blocking());
      assertEquals(List.of(Status.INVALID_HARD), statuses(report));
      assertFalse(report.assessments().getFirst().violations().isEmpty());
    }

    @Test
    void userNamedUnknownFieldIsNeverRetargetedByType() {
      // "warranty" is not a schema field; price is the only NUMBER field allowing GTE.
      QueryIR ir = ir("warranty 3", c("c1", "warranty", GTE, num("3"), "warranty 3"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(report.blocking());
      assertEquals(List.of(Status.INVALID_HARD), statuses(report));
    }

    @Test
    void genericProposalIsBoundToTheOnlyCompatibleFieldByTypeAndUnit() {
      QueryIR ir =
          ir(
              "above 85%",
              c(
                  "c1",
                  "quantity",
                  GT,
                  new IrValue.Quantity(new BigDecimal("85"), "%"),
                  "above 85%"));

      ValidatedQuery q = valid(ir, DEVOPS, DomainMapping.none("devops"));

      assertEquals(List.of("memory_usage"), fields(q.filters()));
      assertTrue(q.filters().getFirst().reason().contains("bound by type"));
    }

    @Test
    void approvedSignalInterpretationIsSoftInferredAndRankingOnly() {
      String text = "lightweight laptop";
      QueryIR ir =
          withSignals(
              ir(text),
              new SemanticSignal(
                  "s1",
                  "lightweight",
                  SemanticSignal.Polarity.POSITIVE,
                  Optional.empty(),
                  Optional.of(Span.of(text, 0, 11)),
                  Confidence.heuristic()));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      ValidatedQuery q = report.validated().orElseThrow();
      assertTrue(q.filters().isEmpty());
      assertEquals(1, q.preferences().size());
      Constraint c = q.preferences().getFirst();
      assertEquals(Strength.SOFT, c.strength());
      assertEquals(Provenance.INFERRED, c.provenance());
      assertEquals("weight", c.field());
      assertEquals(List.of(Status.UNCERTAIN_SOFT), statuses(report));
    }

    @Test
    void signalWithoutAnApprovedRuleProducesNothing() {
      QueryIR ir =
          withSignals(
              ir("not lightweight"),
              new SemanticSignal(
                  "s1",
                  "lightweight",
                  SemanticSignal.Polarity.NEGATIVE,
                  Optional.empty(),
                  Optional.empty(),
                  Confidence.heuristic()));

      ValidatedQuery q = valid(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(q.filters().isEmpty());
      assertTrue(q.preferences().isEmpty());
    }
  }

  @Nested
  class HardAndSoftClassification {

    @Test
    void invalidHardConstraintBlocksAndIsReportedNotDropped() {
      QueryIR ir =
          ir(
              "red with warranty 3",
              c("c1", "color", EQ, text("red"), "red"),
              c("c2", "warranty", GTE, num("3"), "warranty 3"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(report.validated().isEmpty());
      assertEquals(List.of(Status.EXECUTABLE, Status.INVALID_HARD), statuses(report));
      assertEquals(
          List.of("c2"),
          report.blockingAssessments().stream().map(ConstraintAssessment::constraintId).toList());
    }

    @Test
    void unsupportedSoftConstraintIsReportedButDoesNotBlock() {
      QueryIR ir =
          ir(
              "red, ideally warranty 3",
              c("c1", "color", EQ, text("red"), "red"),
              soft(c("c2", "warranty", GTE, num("3"), "warranty 3"), Confidence.deterministic()));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      ValidatedQuery q = report.validated().orElseThrow();
      assertEquals(List.of("c1"), ids(q.filters()));
      assertTrue(q.preferences().isEmpty());
      assertEquals(List.of(Status.EXECUTABLE, Status.UNSUPPORTED_SOFT), statuses(report));
    }

    @Test
    void softConstraintsAreSupportedOrUncertainAndNeverBecomeFilters() {
      QueryIR ir =
          ir(
              "preferably blue, maybe black",
              soft(c("c1", "color", EQ, text("blue"), "blue"), Confidence.deterministic()),
              soft(c("c2", "color", EQ, text("black"), "black"), Confidence.heuristic()));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertEquals(List.of(Status.EXECUTABLE, Status.UNCERTAIN_SOFT), statuses(report));
      ValidatedQuery q = report.validated().orElseThrow();
      assertTrue(q.filters().isEmpty());
      assertEquals(List.of("c1", "c2"), ids(q.preferences()));
    }

    @Test
    void closedVocabularyViolationOnHardCurrencyBlocks() {
      QueryIR ir = ir("under 100 GBP", c("c1", "price", LT, money("100", "GBP"), "under 100 GBP"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(report.blocking());
      assertEquals(List.of(Status.EXECUTABLE, Status.INVALID_HARD), statuses(report));
    }
  }

  @Nested
  class Contradictions {

    @Test
    void emptyNumericIntervalIsContradictory() {
      QueryIR ir =
          ir(
              "over 500 and under 100",
              c("c1", "price", GT, num("500"), "over 500"),
              c("c2", "price", LT, num("100"), "under 100"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertTrue(report.validated().isEmpty());
      assertEquals(List.of(Status.CONTRADICTORY, Status.CONTRADICTORY), statuses(report));
    }

    @Test
    void touchingExclusiveBoundsAreContradictory() {
      QueryIR ir =
          ir(
              "over 100 and under 100",
              c("c1", "price", GT, num("100"), "over 100"),
              c("c2", "price", LTE, num("100"), "under 100"));

      assertTrue(ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING).blocking());
    }

    @Test
    void equalAndNotEqualToTheSameValueIsContradictory() {
      QueryIR ir =
          ir(
              "red but not red",
              c("c1", "color", EQ, text("red"), "red"),
              c("c2", "color", NE, text("red"), "not red"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertEquals(
          List.of("c1", "c2"),
          report.blockingAssessments().stream().map(ConstraintAssessment::constraintId).toList());
    }

    @Test
    void compatibleBoundsAndBetweenRangesAreNotContradictory() {
      QueryIR ir =
          ir(
              "under 100 below 80 between 50 and 90",
              c("c1", "price", LT, num("100"), "under 100"),
              c("c2", "price", LT, num("80"), "below 80"),
              c("c3", "price", BETWEEN, range("50", "90"), "between 50 and 90"));

      assertTrue(
          ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING).validated().isPresent());
    }

    @Test
    void betweenOutsideAnUpperBoundIsContradictory() {
      QueryIR ir =
          ir(
              "under 40 between 50 and 90",
              c("c1", "price", LT, num("40"), "under 40"),
              c("c2", "price", BETWEEN, range("50", "90"), "between 50 and 90"));

      assertTrue(ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING).blocking());
    }

    @Test
    void conflictingSoftPreferencesDoNotBlock() {
      QueryIR ir =
          ir(
              "ideally over 500, ideally under 100",
              soft(c("c1", "price", GT, num("500"), "over 500"), Confidence.deterministic()),
              soft(c("c2", "price", LT, num("100"), "under 100"), Confidence.deterministic()));

      assertTrue(
          ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING).validated().isPresent());
    }

    @Test
    void differentCurrenciesAreNeverCompared() {
      DomainSchema moneySchema =
          new DomainSchema(
              "shop",
              new SchemaVersion(1, 0),
              Map.of("price", FieldDefinition.of("price", FieldType.MONEY, Set.of(LT, GT))),
              Set.of(),
              Set.of("search"));
      QueryIR ir =
          ir(
              "over 500 SEK under 100 EUR",
              c("c1", "price", GT, money("500", "SEK"), "over 500 SEK"),
              c("c2", "price", LT, money("100", "EUR"), "under 100 EUR"));

      assertTrue(
          ConstraintValidator.validate(ir, moneySchema, DomainMapping.none("shop"))
              .validated()
              .isPresent());
    }
  }

  @Nested
  class NegationPreservation {

    @Test
    void exclusionsSurviveMappingWithOperatorAndValue() {
      QueryIR ir =
          ir(
              "not red, not green or blue",
              c("c1", "colour", NE, text("red"), "not red"),
              c(
                  "c2",
                  "color",
                  NOT_IN,
                  new IrValue.ListOf(List.of(text("green"), text("blue"))),
                  "not green or blue"));
      DomainMapping mapping =
          new DomainMapping(
              "commerce", List.of(new MappingRule.FieldAlias("uk", "colour", "color")));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, mapping);

      ValidatedQuery q = report.validated().orElseThrow();
      assertTrue(report.lostNegations().isEmpty());
      assertEquals(List.of(NE, NOT_IN), q.filters().stream().map(Constraint::operator).toList());
      assertEquals(List.of("color", "color"), fields(q.filters()));
    }

    @Test
    void negatedMoneyIsNeverSplitIntoAPositiveCurrencyFilter() {
      QueryIR ir = ir("not 100 SEK", c("c1", "price", NE, money("100", "SEK"), "not 100 SEK"));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      // price does not allow NE and the value stays money: reported and blocking, not rewritten.
      assertTrue(report.blocking());
      assertEquals(List.of(Status.INVALID_HARD), statuses(report));
      assertEquals(
          List.of("c1"),
          report.assessments().stream().map(ConstraintAssessment::constraintId).toList());
    }

    @Test
    void lostOrFlippedExclusionIsDetected() {
      QueryIR before = ir("not red", c("c1", "color", NE, text("red"), "not red"));
      QueryIR flipped = ir("not red", c("c1", "color", EQ, text("red"), "not red"));
      QueryIR dropped = ir("not red");

      assertEquals(List.of("c1"), ConstraintValidator.lostNegations(before, flipped));
      assertEquals(List.of("c1"), ConstraintValidator.lostNegations(before, dropped));
      assertTrue(ConstraintValidator.lostNegations(before, before).isEmpty());
    }
  }

  @Nested
  class CompileBoundary {

    @Test
    void validatedQueryCanOnlyBeCreatedByTheValidator() {
      assertTrue(Modifier.isFinal(ValidatedQuery.class.getModifiers()));
      assertTrue(
          Arrays.stream(ValidatedQuery.class.getDeclaredConstructors())
              .noneMatch(
                  ctor ->
                      Modifier.isPublic(ctor.getModifiers())
                          || Modifier.isProtected(ctor.getModifiers())));
    }

    @Test
    void everyMappedConstraintHasExactlyOneAssessment() {
      QueryIR ir =
          ir(
              "red under 1200 SEK with warranty 3",
              c("c1", "color", EQ, text("red"), "red"),
              c("c2", "amount", LT, money("1200", "SEK"), "under 1200 SEK"),
              soft(c("c3", "warranty", GTE, num("3"), "warranty 3"), Confidence.deterministic()));

      ValidationReport report = ConstraintValidator.validate(ir, COMMERCE, COMMERCE_MAPPING);

      assertEquals(
          List.of("c1", "c2", "c4", "c3"),
          report.assessments().stream().map(ConstraintAssessment::constraintId).toList());
    }
  }

  // ---------------------------------------------------------------------------------------------

  static ValidatedQuery valid(QueryIR ir, DomainSchema schema, DomainMapping mapping) {
    ValidationReport report = ConstraintValidator.validate(ir, schema, mapping);
    assertTrue(report.validated().isPresent(), () -> "blocked: " + report.assessments());
    return report.validated().orElseThrow();
  }

  static List<Status> statuses(ValidationReport report) {
    return report.assessments().stream().map(ConstraintAssessment::status).toList();
  }

  static List<String> fields(List<Constraint> constraints) {
    return constraints.stream().map(Constraint::field).toList();
  }

  static List<String> ids(List<Constraint> constraints) {
    return constraints.stream().map(Constraint::id).toList();
  }

  /** Builds a QueryIR; each constraint's evidence (its reason) becomes a span of {@code text}. */
  static QueryIR ir(String text, Constraint... constraints) {
    List<Constraint> anchored =
        Arrays.stream(constraints)
            .map(
                c -> {
                  int start = text.indexOf(c.reason());
                  if (start < 0) {
                    throw new IllegalArgumentException("evidence not in text: " + c.reason());
                  }
                  return new Constraint(
                      c.id(),
                      c.field(),
                      c.operator(),
                      c.value(),
                      c.strength(),
                      c.provenance(),
                      c.reason(),
                      Optional.of(Span.of(text, start, start + c.reason().length())),
                      c.confidence(),
                      c.scope());
                })
            .toList();
    return new QueryIR(
        IrVersion.CURRENT,
        Optional.empty(),
        text,
        LanguageTag.parse("en").orElseThrow(),
        List.of(),
        List.of(),
        anchored,
        List.of(),
        RetrievalHints.NONE,
        List.of(),
        Map.of());
  }

  static QueryIR withSignals(QueryIR ir, SemanticSignal... signals) {
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        ir.entities(),
        ir.constraints(),
        List.of(signals),
        ir.retrievalHints(),
        ir.subqueries(),
        ir.extensions());
  }

  /**
   * A HARD user-stated constraint; {@code evidence} becomes its span when passed to {@link #ir}.
   */
  static Constraint c(String id, String field, Operator op, IrValue value, String evidence) {
    return new Constraint(
        id,
        field,
        op,
        Optional.of(value),
        Strength.HARD,
        Provenance.USER_TEXT,
        evidence,
        Optional.empty(),
        Confidence.deterministic(),
        Optional.empty());
  }

  static Constraint soft(Constraint c, Confidence confidence) {
    return new Constraint(
        c.id(),
        c.field(),
        c.operator(),
        c.value(),
        Strength.SOFT,
        c.provenance(),
        c.reason(),
        c.span(),
        confidence,
        c.scope());
  }

  static IrValue.Money money(String amount, String currency) {
    return new IrValue.Money(new BigDecimal(amount), Optional.of(currency));
  }

  static IrValue.Number num(String value) {
    return new IrValue.Number(new BigDecimal(value));
  }

  static IrValue.Text text(String value) {
    return new IrValue.Text(value);
  }

  static IrValue.Range range(String min, String max) {
    return new IrValue.Range(Optional.of(num(min)), Optional.of(num(max)), true, true);
  }
}
