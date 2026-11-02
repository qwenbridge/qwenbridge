package io.qwenbridge.core.recovery;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.recovery.ResultAssessment.Outcome;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** QB-125 – QB-130: assessment, typed relaxation policy, consent, validation and reasons. */
class RecoveryTest {

  static Constraint c(String id, String field, Operator op, IrValue value) {
    return c(id, field, op, value, Constraint.Strength.HARD, Constraint.Provenance.USER_TEXT);
  }

  static Constraint c(
      String id,
      String field,
      Operator op,
      IrValue value,
      Constraint.Strength strength,
      Constraint.Provenance provenance) {
    return new Constraint(
        id,
        field,
        op,
        Optional.ofNullable(value),
        strength,
        provenance,
        "test",
        Optional.empty(),
        Confidence.deterministic(),
        Optional.empty());
  }

  static IrValue n(String v) {
    return new IrValue.Number(new BigDecimal(v));
  }

  static IrValue t(String v) {
    return new IrValue.Text(v);
  }

  static final RelaxationPolicy POLICY =
      new RelaxationPolicy(
          "commerce-relaxation",
          "1.0",
          List.of(
              RelaxationRule.raise("price", "1.10"),
              RelaxationRule.drop("size"),
              RelaxationRule.drop("color")),
          Set.of("currency"),
          3,
          0);

  static List<Constraint> query() {
    return List.of(
        c("m1", "brand", Operator.EQ, t("Nike")),
        c("m2", "color", Operator.EQ, t("black")),
        c("m3", "size", Operator.EQ, t("42")),
        c("m4", "price", Operator.LT, n("99.99")),
        c("m5", "currency", Operator.EQ, t("EUR")),
        c("m6", "condition", Operator.NE, t("refurbished")),
        c(
            "m7",
            "color",
            Operator.NE,
            t("pink"),
            Constraint.Strength.HARD,
            Constraint.Provenance.DOMAIN_RULE));
  }

  static final ResultAssessment ZERO = ResultAssessment.of(true, false, 0, 0);

  @Test
  void zeroResultsAreDistinguishedFromProviderFailure() {
    assertEquals(Outcome.ZERO_RESULTS, ZERO.outcome());
    assertEquals(Outcome.PROVIDER_FAILED, ResultAssessment.of(true, true, 0, 0).outcome());
    assertEquals(Outcome.RESULTS, ResultAssessment.of(true, true, 3, 0).outcome());
    assertEquals(Outcome.LOW_RESULTS, ResultAssessment.of(true, false, 2, 5).outcome());
    assertEquals(Outcome.RESULTS, ResultAssessment.of(true, false, 5, 5).outcome());
    assertEquals(Outcome.NOT_EXECUTED, ResultAssessment.of(false, false, 0, 0).outcome());
    var failed =
        RelaxationPlanner.plan(
            POLICY, ResultAssessment.of(true, true, 0, 0), query(), Set.of(), x -> 2);
    assertTrue(failed.proposals().isEmpty(), "a failure is never answered by broadening");
    assertEquals(List.of(RecoveryReason.PROVIDER_FAILED), failed.reasons());
    assertEquals(
        List.of(RecoveryReason.NOT_NEEDED),
        RelaxationPlanner.plan(
                POLICY, ResultAssessment.of(true, false, 4, 0), query(), Set.of(), x -> 2)
            .reasons());
  }

  @Test
  void policiesAreTypedAndCanNeverRelaxProtectedFields() {
    assertEquals("commerce-relaxation@1.0", POLICY.key());
    assertTrue(POLICY.protectedFields().containsAll(Set.of("tenantId", "domain", "currency")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RelaxationPolicy(
                "p", "1", List.of(RelaxationRule.drop("tenantId")), Set.of(), 3, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RelaxationPolicy(
                "p", "1", List.of(RelaxationRule.drop("currency")), Set.of("currency"), 3, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RelaxationPolicy(
                "p",
                "1",
                List.of(RelaxationRule.drop("a"), RelaxationRule.drop("a")),
                Set.of(),
                3,
                0));
    assertThrows(IllegalArgumentException.class, () -> RelaxationRule.raise("price", "3"));
    assertThrows(IllegalArgumentException.class, () -> RelaxationRule.raise("price", "1"));
    assertThrows(IllegalArgumentException.class, () -> RelaxationRule.lower("price", "0.2"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RelaxationPolicy("p", "1", List.of(), Set.of(), 0, 0));
  }

  @Test
  void oneChangePerProposalHardNeedsConsentAndEverySkipHasAReason() {
    var plan = RelaxationPlanner.plan(POLICY, ZERO, query(), Set.of("size"), x -> 2);
    assertEquals("commerce-relaxation@1.0", plan.policy());
    assertEquals(
        List.of("m2", "m4"), plan.proposals().stream().map(Relaxation::constraintId).toList());
    Relaxation color = plan.proposals().get(0);
    assertEquals(RelaxationRule.Kind.DROP_EQUALITY, color.kind());
    assertTrue(color.after().isEmpty());
    assertTrue(color.requiresConsent());
    assertEquals(query().size() - 1, color.alternative().size());
    Relaxation price = plan.proposals().get(1);
    // 99.99 × 1.10 = 109.989, rounded up at 2 decimals: never tighter than the request.
    assertEquals(
        0, new BigDecimal("109.99").compareTo(RelaxationPlanner.number(price.after().get()).get()));
    assertEquals(query().size(), price.alternative().size());
    assertTrue(price.alternative().stream().anyMatch(x -> x.id().equals("m6")), "exclusion kept");

    var reasons =
        plan.skipped().stream()
            .collect(java.util.stream.Collectors.toMap(s -> s.constraintId(), s -> s.reason()));
    assertEquals(RecoveryReason.NO_PERMITTED_RELAXATION, reasons.get("m1"));
    assertEquals(RecoveryReason.LOCKED_BY_USER, reasons.get("m3"));
    assertEquals(RecoveryReason.PROTECTED_FIELD, reasons.get("m5"));
    assertEquals(RecoveryReason.EXCLUSION_NOT_RELAXED, reasons.get("m6"));
    assertEquals(RecoveryReason.DOMAIN_RULE_NOT_RELAXED, reasons.get("m7"));
    assertTrue(
        plan.reasons()
            .containsAll(
                List.of(
                    RecoveryReason.ZERO_RESULTS,
                    RecoveryReason.HARD_REQUIRES_CONSENT,
                    RecoveryReason.RELAXATION_OFFERED)));
    for (Relaxation r : plan.proposals())
      assertTrue(
          AlternativeValidator.validate(POLICY, query(), r.alternative(), Set.of("size")).valid());
  }

  @Test
  void softPreferencesNeedNoConsentAndLimitsApply() {
    var soft =
        List.of(
            c(
                "s1",
                "color",
                Operator.EQ,
                t("red"),
                Constraint.Strength.SOFT,
                Constraint.Provenance.INFERRED));
    var plan = RelaxationPlanner.plan(POLICY, ZERO, soft, Set.of(), x -> 0);
    assertFalse(plan.proposals().getFirst().requiresConsent());
    assertFalse(plan.reasons().contains(RecoveryReason.HARD_REQUIRES_CONSENT));
    var one = new RelaxationPolicy("p", "1", POLICY.rules(), Set.of("currency"), 1, 0);
    var limited = RelaxationPlanner.plan(one, ZERO, query(), Set.of(), x -> 2);
    assertEquals(1, limited.proposals().size());
    assertTrue(
        limited.skipped().stream().anyMatch(s -> s.reason() == RecoveryReason.PROPOSAL_LIMIT));
    // A bound that cannot widen at this scale is not offered.
    var tiny =
        RelaxationPlanner.plan(
            POLICY, ZERO, List.of(c("p", "price", Operator.LTE, n("0"))), Set.of(), x -> 0);
    assertTrue(tiny.proposals().isEmpty());
    // Whole currency units: 1000 × 1.10 = 1100.
    var yen =
        RelaxationPlanner.plan(
            POLICY, ZERO, List.of(c("p", "price", Operator.LT, n("1000"))), Set.of(), x -> 0);
    assertEquals(
        0,
        new BigDecimal("1100")
            .compareTo(RelaxationPlanner.number(yen.proposals().getFirst().after().get()).get()));
    var low =
        RelaxationPlanner.plan(
            POLICY, ResultAssessment.of(true, false, 1, 3), query(), Set.of(), x -> 2);
    assertTrue(low.reasons().contains(RecoveryReason.LOW_RESULTS));
    assertFalse(low.proposals().isEmpty());
  }

  @Test
  void alternativesFromAnySourceAreValidatedAgainstThePolicy() {
    var original = query();
    java.util.function.Function<
            java.util.function.Consumer<List<Constraint>>, AlternativeValidator.Validation>
        alt =
            edit -> {
              var copy = new ArrayList<>(original);
              edit.accept(copy);
              return AlternativeValidator.validate(POLICY, original, copy, Set.of("size"));
            };
    assertEquals(List.of(RecoveryReason.ALTERNATIVE_NO_CHANGE), alt.apply(x -> {}).reasons());
    assertTrue(alt.apply(x -> x.removeIf(k -> k.id().equals("m2"))).valid(), "drop color");
    assertTrue(alt.apply(x -> x.set(3, c("m4", "price", Operator.LT, n("105")))).valid());
    assertTrue(
        alt.apply(x -> x.set(3, c("m4", "price", Operator.LT, n("99.990"))))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_NO_CHANGE),
        "same value, other scale");
    assertTrue(
        alt.apply(x -> x.set(3, c("m4", "price", Operator.LT, n("200"))))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_NOT_PERMITTED));
    assertTrue(
        alt.apply(x -> x.set(3, c("m4", "price", Operator.LTE, n("105"))))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_ADDS_CONSTRAINT));
    assertTrue(
        alt.apply(x -> x.removeIf(k -> k.id().equals("m6")))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_REMOVES_EXCLUSION));
    assertTrue(
        alt.apply(x -> x.removeIf(k -> k.id().equals("m5")))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_CHANGES_PROTECTED));
    assertTrue(
        alt.apply(x -> x.removeIf(k -> k.id().equals("m7")))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_CHANGES_PROTECTED));
    assertTrue(
        alt.apply(x -> x.removeIf(k -> k.id().equals("m3")))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_CHANGES_LOCKED));
    assertTrue(
        alt.apply(x -> x.removeIf(k -> k.id().equals("m1")))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_NOT_PERMITTED));
    assertTrue(
        alt.apply(x -> x.add(c("x", "material", Operator.EQ, t("leather"))))
            .reasons()
            .contains(RecoveryReason.ALTERNATIVE_ADDS_CONSTRAINT));
    // Money keeps its currency: switching currency is a conversion, not a relaxation.
    var money =
        List.of(
            c(
                "p",
                "price",
                Operator.LT,
                new IrValue.Money(new BigDecimal("100"), Optional.of("EUR"))));
    var usd =
        List.of(
            c(
                "p",
                "price",
                Operator.LT,
                new IrValue.Money(new BigDecimal("105"), Optional.of("USD"))));
    assertFalse(AlternativeValidator.validate(POLICY, money, usd, Set.of()).valid());
  }
}
