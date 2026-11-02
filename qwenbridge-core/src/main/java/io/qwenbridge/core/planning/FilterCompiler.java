package io.qwenbridge.core.planning;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.validation.ValidatedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compiles the HARD constraints of a {@link ValidatedQuery} into the backend-neutral {@link Filter}
 * AST (QB-100). It accepts nothing else, so backend queries can only ever come from validated,
 * schema-checked constraints, never from model output.
 *
 * <p>A HARD constraint that cannot be expressed is never dropped: the whole compilation fails with
 * the constraint ids, because silently weakening a requirement would return results the user
 * excluded. Constraints scoped to a subquery are compiled by that subquery's plan, not here.
 */
public final class FilterCompiler {

  private FilterCompiler() {}

  /** Either a filter or the constraints that could not be compiled. */
  public sealed interface Result {
    record Compiled(Filter filter, List<String> constraintIds) implements Result {
      public Compiled {
        constraintIds = List.copyOf(constraintIds);
      }
    }

    record NotCompilable(List<String> constraintIds, List<String> reasons) implements Result {
      public NotCompilable {
        constraintIds = List.copyOf(constraintIds);
        reasons = List.copyOf(reasons);
      }
    }
  }

  public static Result compile(ValidatedQuery query) {
    List<Filter> parts = new ArrayList<>();
    List<String> compiled = new ArrayList<>();
    List<String> failedIds = new ArrayList<>();
    List<String> reasons = new ArrayList<>();
    for (Constraint c : query.filters()) {
      if (c.scope().isPresent()) {
        continue;
      }
      Optional<Filter> f = compile(c);
      if (f.isPresent()) {
        parts.add(f.get());
        compiled.add(c.id());
      } else {
        failedIds.add(c.id());
        reasons.add(
            c.id() + ": " + c.operator() + " on " + c.value().map(IrValue::type).orElse("none"));
      }
    }
    if (!failedIds.isEmpty()) {
      return new Result.NotCompilable(failedIds, reasons);
    }
    Filter filter =
        parts.isEmpty() ? Filter.ALL : parts.size() == 1 ? parts.getFirst() : new Filter.And(parts);
    return new Result.Compiled(filter, compiled);
  }

  /**
   * One constraint as a filter, for ranking with SOFT preferences (QB-118): a preference is
   * evaluated per result, never applied as a filter. Empty when the value cannot be compiled.
   */
  public static Optional<Filter> preference(Constraint c) {
    return compile(c);
  }

  static Optional<Filter> compile(Constraint c) {
    String field = c.field();
    Optional<IrValue> value = c.value();
    return switch (c.operator()) {
      case EXISTS -> Optional.of(new Filter.Exists(field));
      case NOT_EXISTS -> Optional.of(new Filter.Not(new Filter.Exists(field)));
      case EQ, NE, LT, LTE, GT, GTE ->
          value
              .flatMap(QueryValues::scalar)
              .filter(v -> !c.operator().ordering() || v instanceof FieldValue.Number)
              .map(v -> new Filter.Compare(field, op(c.operator()), v));
      case BETWEEN -> value.flatMap(v -> range(field, v));
      case IN -> value.flatMap(QueryValues::list).map(vs -> new Filter.In(field, vs));
      case NOT_IN ->
          value.flatMap(QueryValues::list).map(vs -> new Filter.Not(new Filter.In(field, vs)));
      case CONTAINS -> value.flatMap(QueryValues::text).map(t -> new Filter.Contains(field, t));
      case NOT_CONTAINS ->
          value.flatMap(QueryValues::text).map(t -> new Filter.Not(new Filter.Contains(field, t)));
    };
  }

  private static Optional<Filter> range(String field, IrValue value) {
    if (!(value instanceof IrValue.Range r)) {
      return Optional.empty();
    }
    List<Filter> bounds = new ArrayList<>();
    if (r.min().isPresent()) {
      Optional<FieldValue> min =
          QueryValues.scalar(r.min().get()).filter(v -> v instanceof FieldValue.Number);
      if (min.isEmpty()) {
        return Optional.empty();
      }
      bounds.add(
          new Filter.Compare(
              field, r.minInclusive() ? Filter.Compare.Op.GTE : Filter.Compare.Op.GT, min.get()));
    }
    if (r.max().isPresent()) {
      Optional<FieldValue> max =
          QueryValues.scalar(r.max().get()).filter(v -> v instanceof FieldValue.Number);
      if (max.isEmpty()) {
        return Optional.empty();
      }
      bounds.add(
          new Filter.Compare(
              field, r.maxInclusive() ? Filter.Compare.Op.LTE : Filter.Compare.Op.LT, max.get()));
    }
    return Optional.of(bounds.size() == 1 ? bounds.getFirst() : new Filter.And(bounds));
  }

  private static Filter.Compare.Op op(Constraint.Operator op) {
    return switch (op) {
      case EQ -> Filter.Compare.Op.EQ;
      case NE -> Filter.Compare.Op.NE;
      case LT -> Filter.Compare.Op.LT;
      case LTE -> Filter.Compare.Op.LTE;
      case GT -> Filter.Compare.Op.GT;
      case GTE -> Filter.Compare.Op.GTE;
      default -> throw new IllegalArgumentException("not a comparison: " + op);
    };
  }

  /** IrValue to FieldValue conversion. Units and currencies were resolved by the mapper. */
  static final class QueryValues {
    private QueryValues() {}

    static Optional<FieldValue> scalar(IrValue v) {
      return switch (v) {
        case IrValue.Text t -> Optional.of(new FieldValue.Text(t.value()));
        case IrValue.Number n -> Optional.of(new FieldValue.Number(n.value()));
        case IrValue.Quantity q -> Optional.of(new FieldValue.Number(q.value()));
        case IrValue.Money m -> Optional.of(new FieldValue.Number(m.amount()));
        case IrValue.Bool b -> Optional.of(new FieldValue.Bool(b.value()));
        case IrValue.Temporal t -> Optional.empty();
        case IrValue.Range r -> Optional.empty();
        case IrValue.ListOf l -> Optional.empty();
      };
    }

    static Optional<List<FieldValue>> list(IrValue v) {
      if (v instanceof IrValue.ListOf l) {
        List<FieldValue> out = new ArrayList<>();
        for (IrValue item : l.values()) {
          Optional<FieldValue> s = scalar(item);
          if (s.isEmpty()) {
            return Optional.empty();
          }
          out.add(s.get());
        }
        return out.isEmpty() ? Optional.empty() : Optional.of(out);
      }
      return scalar(v).map(List::of);
    }

    static Optional<String> text(IrValue v) {
      return v instanceof IrValue.Text t ? Optional.of(t.value()) : Optional.empty();
    }
  }
}
