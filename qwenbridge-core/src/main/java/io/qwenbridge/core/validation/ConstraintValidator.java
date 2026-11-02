package io.qwenbridge.core.validation;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.Constraint.Provenance;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.SchemaValidator;
import io.qwenbridge.core.schema.SchemaViolation;
import io.qwenbridge.core.validation.ConstraintAssessment.Status;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The constraint validation boundary (QB-050–QB-055).
 *
 * <ol>
 *   <li>Map the QueryIR to the schema with approved rules ({@link SemanticMapper}).
 *   <li>Check every constraint against the schema. An invalid HARD constraint is {@code
 *       INVALID_HARD} and blocks: it is never weakened or silently removed to get more results. An
 *       invalid SOFT one is {@code UNSUPPORTED_SOFT}: reported, not compiled.
 *   <li>Valid SOFT constraints from uncertain sources (inferred, heuristic or model decisions) are
 *       {@code UNCERTAIN_SOFT}: usable for ranking only, never upgraded to filters.
 *   <li>HARD constraints on the same field that cannot all hold (empty numeric interval, or equal
 *       and not-equal to the same value) are {@code CONTRADICTORY} and block.
 *   <li>Every exclusion of the input must still be an exclusion after mapping; otherwise the
 *       transformation is defective and execution is blocked.
 * </ol>
 *
 * Only when nothing blocks is a {@link ValidatedQuery} produced.
 */
public final class ConstraintValidator {

  private ConstraintValidator() {}

  public static ValidationReport validate(
      QueryIR input, DomainSchema schema, DomainMapping mapping) {
    QueryIR ir = SemanticMapper.map(input, schema, mapping);
    List<String> lostNegations = lostNegations(input, ir);

    List<SchemaViolation> structural = new ArrayList<>();
    QueryIrValidator.validate(ir)
        .forEach(
            v ->
                structural.add(
                    new SchemaViolation(
                        v.path(), SchemaViolation.Code.TYPE_MISMATCH, v.message())));
    Map<Integer, List<SchemaViolation>> byIndex = new HashMap<>();
    for (SchemaViolation v : SchemaValidator.validate(ir, schema)) {
      if (v.path().startsWith("/constraints/")) {
        int index = Integer.parseInt(v.path().split("/")[2]);
        byIndex.computeIfAbsent(index, i -> new ArrayList<>()).add(v);
      }
    }
    Set<String> contradictory = contradictions(ir.constraints());

    List<ConstraintAssessment> assessments = new ArrayList<>();
    List<Constraint> filters = new ArrayList<>();
    List<Constraint> preferences = new ArrayList<>();
    for (int i = 0; i < ir.constraints().size(); i++) {
      Constraint c = ir.constraints().get(i);
      List<SchemaViolation> violations = byIndex.getOrDefault(i, List.of());
      boolean hard = c.strength() == Strength.HARD;
      Status status;
      String note;
      if (!violations.isEmpty()) {
        status = hard ? Status.INVALID_HARD : Status.UNSUPPORTED_SOFT;
        note =
            hard
                ? "required but not expressible in schema " + schema.key()
                : "preference not expressible; ignored";
      } else if (hard && contradictory.contains(c.id())) {
        status = Status.CONTRADICTORY;
        note = "conflicts with another requirement on " + c.field();
      } else if (!hard && uncertain(c)) {
        status = Status.UNCERTAIN_SOFT;
        note = "uncertain preference; ranking only";
        preferences.add(c);
      } else {
        status = Status.EXECUTABLE;
        note = hard ? "filter" : "preference";
        (hard ? filters : preferences).add(c);
      }
      assessments.add(new ConstraintAssessment(c.id(), status, violations, note));
    }
    boolean blocking =
        !structural.isEmpty()
            || !lostNegations.isEmpty()
            || assessments.stream().anyMatch(ConstraintAssessment::blocking);
    Optional<ValidatedQuery> validated =
        blocking
            ? Optional.empty()
            : Optional.of(new ValidatedQuery(ir, schema.key(), filters, preferences));
    return new ValidationReport(validated, assessments, lostNegations);
  }

  static boolean uncertain(Constraint c) {
    Confidence.Kind kind = c.confidence().kind();
    return c.provenance() == Provenance.INFERRED
        || kind == Confidence.Kind.HEURISTIC
        || kind == Confidence.Kind.MODEL_DECISION
        || kind == Confidence.Kind.UNKNOWN;
  }

  /** Input exclusion ids that are no longer exclusions (or missing) after mapping (QB-055). */
  static List<String> lostNegations(QueryIR before, QueryIR after) {
    Map<String, Constraint> mapped =
        after.constraints().stream().collect(Collectors.toMap(Constraint::id, c -> c, (a, b) -> a));
    List<String> lost = new ArrayList<>();
    for (Constraint c : before.negativeConstraints()) {
      Constraint m = mapped.get(c.id());
      if (m == null
          || !m.negative()
          || m.operator() != c.operator()
          || !m.value().equals(c.value())) {
        lost.add(c.id());
      }
    }
    return lost;
  }

  /** Ids of HARD constraints that cannot all hold together on their field. */
  static Set<String> contradictions(List<Constraint> constraints) {
    Map<String, List<Constraint>> byField =
        constraints.stream()
            .filter(c -> c.strength() == Strength.HARD && c.scope().isEmpty())
            .collect(Collectors.groupingBy(Constraint::field));
    Set<String> ids = new java.util.HashSet<>();
    for (List<Constraint> group : byField.values()) {
      if (group.size() < 2) {
        continue;
      }
      Bound lower = null;
      Bound upper = null;
      for (Constraint c : group) {
        for (Bound b : bounds(c)) {
          if (b.lower() && (lower == null || stronger(b, lower, true))) {
            lower = b;
          }
          if (!b.lower() && (upper == null || stronger(b, upper, false))) {
            upper = b;
          }
        }
      }
      if (lower != null && upper != null && lower.comparableWith(upper)) {
        int cmp = lower.value().compareTo(upper.value());
        if (cmp > 0 || cmp == 0 && (!lower.inclusive() || !upper.inclusive())) {
          ids.add(lower.constraintId());
          ids.add(upper.constraintId());
        }
      }
      for (Constraint eq : group) {
        if (eq.operator() != Operator.EQ) {
          continue;
        }
        for (Constraint ne : group) {
          if (ne.operator() == Operator.NE && ne.value().equals(eq.value())) {
            ids.add(eq.id());
            ids.add(ne.id());
          }
        }
      }
    }
    return ids;
  }

  private record Bound(
      String constraintId, BigDecimal value, String kind, boolean lower, boolean inclusive) {
    boolean comparableWith(Bound other) {
      return kind.equals(other.kind);
    }
  }

  private static List<Bound> bounds(Constraint c) {
    if (c.value().isEmpty()) {
      return List.of();
    }
    IrValue v = c.value().get();
    return switch (c.operator()) {
      case LT ->
          numeric(v)
              .map(n -> List.of(new Bound(c.id(), n.value, n.kind, false, false)))
              .orElse(List.of());
      case LTE ->
          numeric(v)
              .map(n -> List.of(new Bound(c.id(), n.value, n.kind, false, true)))
              .orElse(List.of());
      case GT ->
          numeric(v)
              .map(n -> List.of(new Bound(c.id(), n.value, n.kind, true, false)))
              .orElse(List.of());
      case GTE ->
          numeric(v)
              .map(n -> List.of(new Bound(c.id(), n.value, n.kind, true, true)))
              .orElse(List.of());
      case EQ ->
          numeric(v)
              .map(
                  n ->
                      List.of(
                          new Bound(c.id(), n.value, n.kind, true, true),
                          new Bound(c.id(), n.value, n.kind, false, true)))
              .orElse(List.of());
      case BETWEEN -> {
        IrValue.Range r = (IrValue.Range) v;
        List<Bound> out = new ArrayList<>();
        r.min()
            .flatMap(ConstraintValidator::numeric)
            .ifPresent(n -> out.add(new Bound(c.id(), n.value, n.kind, true, r.minInclusive())));
        r.max()
            .flatMap(ConstraintValidator::numeric)
            .ifPresent(n -> out.add(new Bound(c.id(), n.value, n.kind, false, r.maxInclusive())));
        yield out;
      }
      default -> List.of();
    };
  }

  private record Numeric(BigDecimal value, String kind) {}

  private static Optional<Numeric> numeric(IrValue v) {
    return switch (v) {
      case IrValue.Number n -> Optional.of(new Numeric(n.value(), "number"));
      case IrValue.Quantity q -> Optional.of(new Numeric(q.value(), "quantity:" + q.unit()));
      case IrValue.Money m ->
          Optional.of(new Numeric(m.amount(), "money:" + m.currency().orElse("?")));
      default -> Optional.empty();
    };
  }

  private static boolean stronger(Bound candidate, Bound current, boolean lower) {
    if (!candidate.comparableWith(current)) {
      return false;
    }
    int cmp = candidate.value().compareTo(current.value());
    if (cmp == 0) {
      return !candidate.inclusive() && current.inclusive();
    }
    return lower ? cmp > 0 : cmp < 0;
  }
}
