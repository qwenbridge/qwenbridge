package io.qwenbridge.core.recovery;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates an alternative query against the original before it is offered or executed (QB-129).
 * Alternatives may come from the relaxation planner, from a stored offer or from a model's rewrite
 * suggestion; all pass the same check. An alternative is valid only when:
 *
 * <ul>
 *   <li>it changes at least one constraint, and each change is a relaxation a policy rule permits
 *       (a bound widened within the rule's factor, or an equality the rule may drop);
 *   <li>it adds nothing: no new constraint, no different operator;
 *   <li>it keeps every exclusion, protected field, domain-rule constraint and locked field exactly
 *       as it was.
 * </ul>
 */
public final class AlternativeValidator {

  public record Validation(
      boolean valid, List<RecoveryReason> reasons, List<String> changedFields) {
    public Validation {
      reasons = List.copyOf(reasons);
      changedFields = List.copyOf(changedFields);
    }
  }

  private AlternativeValidator() {}

  public static Validation validate(
      RelaxationPolicy policy,
      List<Constraint> original,
      List<Constraint> alternative,
      Set<String> lockedFields) {
    List<Constraint> removed = new ArrayList<>(original);
    List<Constraint> added = new ArrayList<>();
    for (Constraint a : alternative) {
      Optional<Constraint> same = removed.stream().filter(o -> sameMeaning(o, a)).findFirst();
      if (same.isPresent()) {
        removed.remove(same.get());
      } else {
        added.add(a);
      }
    }
    LinkedHashSet<RecoveryReason> problems = new LinkedHashSet<>();
    List<String> changed = new ArrayList<>();
    for (Constraint a : added) {
      Optional<Constraint> widened =
          removed.stream()
              .filter(o -> o.field().equals(a.field()) && o.operator() == a.operator())
              .findFirst();
      if (widened.isEmpty()) {
        problems.add(RecoveryReason.ALTERNATIVE_ADDS_CONSTRAINT);
        continue;
      }
      Constraint o = widened.get();
      removed.remove(o);
      check(policy, o, lockedFields, problems);
      if (!permittedWidening(policy, o, a)) {
        problems.add(RecoveryReason.ALTERNATIVE_NOT_PERMITTED);
      }
      changed.add(o.field());
    }
    for (Constraint o : removed) {
      check(policy, o, lockedFields, problems);
      boolean droppable =
          o.operator() == Constraint.Operator.EQ
              && policy.rules().stream()
                  .anyMatch(
                      r ->
                          r.field().equals(o.field())
                              && r.kind() == RelaxationRule.Kind.DROP_EQUALITY);
      if (!droppable) {
        problems.add(RecoveryReason.ALTERNATIVE_NOT_PERMITTED);
      }
      changed.add(o.field());
    }
    if (changed.isEmpty() && problems.isEmpty()) {
      problems.add(RecoveryReason.ALTERNATIVE_NO_CHANGE);
    }
    return new Validation(problems.isEmpty(), List.copyOf(problems), changed);
  }

  private static void check(
      RelaxationPolicy policy, Constraint o, Set<String> locked, Set<RecoveryReason> problems) {
    if (o.negative()) {
      problems.add(RecoveryReason.ALTERNATIVE_REMOVES_EXCLUSION);
    }
    if (policy.protectedFields().contains(o.field())
        || o.provenance() == Constraint.Provenance.DOMAIN_RULE) {
      problems.add(RecoveryReason.ALTERNATIVE_CHANGES_PROTECTED);
    }
    if (locked.contains(o.field())) {
      problems.add(RecoveryReason.ALTERNATIVE_CHANGES_LOCKED);
    }
  }

  private static boolean permittedWidening(RelaxationPolicy policy, Constraint o, Constraint a) {
    Optional<BigDecimal> before = RelaxationPlanner.number(o);
    Optional<BigDecimal> after = RelaxationPlanner.number(a);
    if (before.isEmpty() || after.isEmpty() || !sameUnit(o, a)) {
      return false;
    }
    for (RelaxationRule rule : policy.rules()) {
      if (!rule.field().equals(o.field()) || !RelaxationPlanner.fits(rule, o)) {
        continue;
      }
      BigDecimal limit = before.get().multiply(rule.factor());
      if (rule.kind() == RelaxationRule.Kind.RAISE_UPPER_BOUND
          && after.get().compareTo(before.get()) > 0
          && after
                  .get()
                  .compareTo(
                      limit.setScale(
                          Math.max(0, after.get().scale()), java.math.RoundingMode.CEILING))
              <= 0) {
        return true;
      }
      if (rule.kind() == RelaxationRule.Kind.LOWER_LOWER_BOUND
          && after.get().compareTo(before.get()) < 0
          && after
                  .get()
                  .compareTo(
                      limit.setScale(
                          Math.max(0, after.get().scale()), java.math.RoundingMode.FLOOR))
              >= 0) {
        return true;
      }
    }
    return false;
  }

  /** Money keeps its currency and quantities their unit: a change of either is not a relaxation. */
  private static boolean sameUnit(Constraint o, Constraint a) {
    var x = o.value().orElse(null);
    var y = a.value().orElse(null);
    if (x instanceof IrValue.Money m && y instanceof IrValue.Money n) {
      return m.currency().equals(n.currency());
    }
    if (x instanceof IrValue.Quantity p && y instanceof IrValue.Quantity q) {
      return p.unit().equals(q.unit());
    }
    return x != null && y != null && x.getClass() == y.getClass();
  }

  /** Same field, operator and value (numbers compared by value, not scale). */
  static boolean sameMeaning(Constraint a, Constraint b) {
    if (!a.field().equals(b.field()) || a.operator() != b.operator()) {
      return false;
    }
    var x = RelaxationPlanner.number(a);
    var y = RelaxationPlanner.number(b);
    if (x.isPresent() && y.isPresent()) {
      return x.get().compareTo(y.get()) == 0 && sameUnit(a, b);
    }
    return a.value().equals(b.value());
  }
}
