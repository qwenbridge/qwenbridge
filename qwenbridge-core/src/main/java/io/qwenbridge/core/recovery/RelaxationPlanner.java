package io.qwenbridge.core.recovery;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Plans zero/low-result recovery (QB-125 – QB-130). It only proposes; nothing is executed or
 * broadened here.
 *
 * <ul>
 *   <li>Only a recoverable assessment (zero or low results from a working provider) gets proposals;
 *       a provider failure never does.
 *   <li>Each proposal changes exactly one constraint as a policy rule permits, keeps every other
 *       constraint and is checked by the {@link AlternativeValidator} before it is offered.
 *   <li>Exclusions, protected fields, constraints from domain rules and locked fields are never
 *       relaxed; each is reported with its reason.
 *   <li>A relaxed HARD constraint requires the user's consent.
 * </ul>
 */
public final class RelaxationPlanner {

  private RelaxationPlanner() {}

  /**
   * @param scale decimal places of a relaxed bound (for money: the currency's minor units); the
   *     relaxed value is rounded up for upper bounds and down for lower bounds, never tighter than
   *     before
   */
  public static RecoveryPlan plan(
      RelaxationPolicy policy,
      ResultAssessment assessment,
      List<Constraint> constraints,
      Set<String> lockedFields,
      ToIntFunction<Constraint> scale) {
    if (!assessment.recoverable()) {
      RecoveryReason reason =
          switch (assessment.outcome()) {
            case PROVIDER_FAILED -> RecoveryReason.PROVIDER_FAILED;
            case NOT_EXECUTED -> RecoveryReason.NOT_EXECUTED;
            default -> RecoveryReason.NOT_NEEDED;
          };
      return new RecoveryPlan(policy.key(), assessment, List.of(), List.of(), List.of(reason));
    }
    LinkedHashSet<RecoveryReason> reasons = new LinkedHashSet<>();
    reasons.add(
        assessment.outcome() == ResultAssessment.Outcome.ZERO_RESULTS
            ? RecoveryReason.ZERO_RESULTS
            : RecoveryReason.LOW_RESULTS);
    List<Relaxation> proposals = new ArrayList<>();
    List<RecoveryPlan.Skipped> skipped = new ArrayList<>();
    for (Constraint c : constraints) {
      Optional<RecoveryReason> blocked = blocked(policy, c, lockedFields);
      if (blocked.isPresent()) {
        skipped.add(new RecoveryPlan.Skipped(c.id(), c.field(), blocked.get()));
        continue;
      }
      Optional<RelaxationRule> rule =
          policy.rules().stream()
              .filter(r -> r.field().equals(c.field()) && fits(r, c))
              .findFirst();
      if (rule.isEmpty()) {
        skipped.add(
            new RecoveryPlan.Skipped(c.id(), c.field(), RecoveryReason.NO_PERMITTED_RELAXATION));
        continue;
      }
      if (proposals.size() >= policy.maxProposals()) {
        skipped.add(new RecoveryPlan.Skipped(c.id(), c.field(), RecoveryReason.PROPOSAL_LIMIT));
        continue;
      }
      Optional<Constraint> after = relax(rule.get(), c, scale.applyAsInt(c));
      if (rule.get().kind() != RelaxationRule.Kind.DROP_EQUALITY && after.isEmpty()) {
        skipped.add(
            new RecoveryPlan.Skipped(c.id(), c.field(), RecoveryReason.NO_PERMITTED_RELAXATION));
        continue;
      }
      List<Constraint> alternative = new ArrayList<>();
      for (Constraint other : constraints) {
        if (other != c) {
          alternative.add(other);
        } else {
          after.ifPresent(alternative::add);
        }
      }
      var validation =
          AlternativeValidator.validate(policy, constraints, alternative, lockedFields);
      if (!validation.valid()) {
        // Defensive: the planner's own proposal must pass the same check as any alternative.
        skipped.add(new RecoveryPlan.Skipped(c.id(), c.field(), validation.reasons().getFirst()));
        continue;
      }
      boolean hard = c.strength() == Constraint.Strength.HARD;
      if (hard) {
        reasons.add(RecoveryReason.HARD_REQUIRES_CONSENT);
      }
      proposals.add(
          new Relaxation(c.id(), c.field(), rule.get().kind(), c, after, alternative, hard));
    }
    if (!proposals.isEmpty()) {
      reasons.add(RecoveryReason.RELAXATION_OFFERED);
    }
    skipped.forEach(s -> reasons.add(s.reason()));
    return new RecoveryPlan(policy.key(), assessment, proposals, skipped, List.copyOf(reasons));
  }

  static Optional<RecoveryReason> blocked(
      RelaxationPolicy policy, Constraint c, Set<String> lockedFields) {
    if (policy.protectedFields().contains(c.field())) {
      return Optional.of(RecoveryReason.PROTECTED_FIELD);
    }
    if (c.provenance() == Constraint.Provenance.DOMAIN_RULE) {
      return Optional.of(RecoveryReason.DOMAIN_RULE_NOT_RELAXED);
    }
    if (c.negative()) {
      return Optional.of(RecoveryReason.EXCLUSION_NOT_RELAXED);
    }
    if (lockedFields.contains(c.field())) {
      return Optional.of(RecoveryReason.LOCKED_BY_USER);
    }
    return Optional.empty();
  }

  static boolean fits(RelaxationRule rule, Constraint c) {
    return switch (rule.kind()) {
      case RAISE_UPPER_BOUND ->
          (c.operator() == Operator.LT || c.operator() == Operator.LTE) && number(c).isPresent();
      case LOWER_LOWER_BOUND ->
          (c.operator() == Operator.GT || c.operator() == Operator.GTE) && number(c).isPresent();
      case DROP_EQUALITY -> c.operator() == Operator.EQ;
    };
  }

  private static Optional<Constraint> relax(RelaxationRule rule, Constraint c, int scale) {
    if (rule.kind() == RelaxationRule.Kind.DROP_EQUALITY) {
      return Optional.empty();
    }
    BigDecimal old = number(c).orElseThrow();
    BigDecimal relaxed =
        old.multiply(rule.factor())
            .setScale(
                Math.max(0, scale),
                rule.kind() == RelaxationRule.Kind.RAISE_UPPER_BOUND
                    ? RoundingMode.CEILING
                    : RoundingMode.FLOOR);
    boolean wider =
        rule.kind() == RelaxationRule.Kind.RAISE_UPPER_BOUND
            ? relaxed.compareTo(old) > 0
            : relaxed.compareTo(old) < 0;
    if (!wider) {
      return Optional.empty();
    }
    return Optional.of(
        new Constraint(
            c.id(),
            c.field(),
            c.operator(),
            Optional.of(withNumber(c.value().orElseThrow(), relaxed)),
            c.strength(),
            c.provenance(),
            c.reason(),
            c.span(),
            c.confidence(),
            c.scope()));
  }

  /** The numeric value of a constraint (number, money amount or quantity), if any. */
  public static Optional<BigDecimal> number(Constraint c) {
    return c.value()
        .flatMap(
            v ->
                switch (v) {
                  case IrValue.Number n -> Optional.of(n.value());
                  case IrValue.Money m -> Optional.of(m.amount());
                  case IrValue.Quantity q -> Optional.of(q.value());
                  default -> Optional.empty();
                });
  }

  private static IrValue withNumber(IrValue value, BigDecimal number) {
    return switch (value) {
      case IrValue.Number n -> new IrValue.Number(number);
      case IrValue.Money m -> new IrValue.Money(number, m.currency());
      case IrValue.Quantity q -> new IrValue.Quantity(number, q.unit());
      default -> throw new IllegalArgumentException("not numeric");
    };
  }
}
