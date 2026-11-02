package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A compiler shared by every public search mode; field names come from a registered schema. */
public final class CommerceCompiler {
  private CommerceCompiler() {}

  /** The kinds of a rejected plan, as targeted clarification kinds (QB-135). */
  public enum Kind {
    CONFLICT,
    MISSING_QUALIFIER,
    UNSUPPORTED,
    INVALID_VALUE,
    VAGUE
  }

  /**
   * A plan the schema rejects, with what a targeted question needs: the kind, the field and the
   * values involved. The message is unchanged from before (apiVersion 2 clients see it as is).
   */
  public static final class Rejected extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final Kind kind;
    private final String field;
    private final transient List<String> options;

    Rejected(String message, Kind kind, String field, List<String> options) {
      this(message, kind, field, options, null);
    }

    Rejected(String message, Kind kind, String field, List<String> options, Throwable cause) {
      super(message, cause);
      this.kind = kind;
      this.field = field;
      this.options = List.copyOf(options);
    }

    public Kind kind() {
      return kind;
    }

    public java.util.Optional<String> field() {
      return java.util.Optional.ofNullable(field);
    }

    public List<String> options() {
      return options;
    }
  }

  public static Plan validate(Schema schema, String tenant, List<Constraint> constraints) {
    if (constraints.size() > 32)
      throw new Rejected("Too many constraints", Kind.VAGUE, null, List.of());
    Map<String, List<Constraint>> grouped = new HashMap<>();
    for (Constraint c : constraints) {
      Field f = schema.fields().get(c.field());
      if (f == null || !f.operators().contains(c.operator()))
        throw new Rejected(
            "Unsupported field/operator: " + c.field(), Kind.UNSUPPORTED, c.field(), List.of());
      try {
        value(f, c.value());
      } catch (IllegalArgumentException ex) {
        throw new Rejected(ex.getMessage(), Kind.INVALID_VALUE, c.field(), List.of(), ex);
      }
      if (c.field().equals("currency")) {
        try {
          java.util.Currency.getInstance(c.value().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new Rejected(
              "Unsupported ISO currency: " + c.value(),
              Kind.INVALID_VALUE,
              "currency",
              List.of(),
              ex);
        }
      }
      grouped.computeIfAbsent(c.field(), ignored -> new ArrayList<>()).add(c);
    }
    grouped.forEach(
        (name, group) -> {
          Field field = schema.fields().get(name);
          Set<String> equalities =
              group.stream()
                  .filter(c -> c.operator() == Operator.EQ)
                  .map(c -> value(field, c.value()).toString())
                  .collect(java.util.stream.Collectors.toSet());
          if (equalities.size() > 1)
            throw new Rejected(
                "Conflicting equality: " + name,
                Kind.CONFLICT,
                name,
                group.stream()
                    .filter(c -> c.operator() == Operator.EQ)
                    .map(Constraint::value)
                    .distinct()
                    .sorted()
                    .toList());
          for (Constraint eq : group)
            if (eq.operator() == Operator.EQ)
              for (Constraint c : group)
                if (!accepts(field, value(field, eq.value()), c))
                  throw new Rejected(
                      "Contradictory constraint: " + name,
                      Kind.CONFLICT,
                      name,
                      group.stream().map(x -> x.operator() + " " + x.value()).toList());
          if (field.type() == ValueType.DECIMAL || field.type() == ValueType.DATE) {
            for (Constraint lower : group)
              for (Constraint upper : group) {
                if (Set.of(Operator.GT, Operator.GTE).contains(lower.operator())
                    && Set.of(Operator.LT, Operator.LTE).contains(upper.operator())) {
                  int cmp = compare(value(field, lower.value()), value(field, upper.value()));
                  if (cmp > 0
                      || (cmp == 0
                          && (lower.operator() == Operator.GT || upper.operator() == Operator.LT)))
                    throw new Rejected(
                        "Empty range: " + name,
                        Kind.CONFLICT,
                        name,
                        List.of(
                            lower.operator() + " " + lower.value(),
                            upper.operator() + " " + upper.value()));
                }
              }
          }
        });
    if (grouped.containsKey("price") && !grouped.containsKey("currency"))
      throw new Rejected(
          "Price requires an explicit currency", Kind.MISSING_QUALIFIER, "currency", List.of());
    return new Plan(1, schema.id(), schema.version(), tenant, constraints, schema.fields());
  }

  public static Object value(Field field, String text) {
    return switch (field.type()) {
      case TEXT -> {
        if (text.isBlank()) throw new IllegalArgumentException("Empty term");
        yield switch (field.normalization()) {
          case NONE -> text;
          case LOWERCASE -> text.toLowerCase(java.util.Locale.ROOT);
          case UPPERCASE -> text.toUpperCase(java.util.Locale.ROOT);
        };
      }
      case DECIMAL -> {
        BigDecimal n = new BigDecimal(text);
        if (n.signum() < 0 || n.precision() > 20 || Math.abs(n.scale()) > 8)
          throw new IllegalArgumentException("Invalid numeric bound");
        yield n.stripTrailingZeros();
      }
      case BOOLEAN -> {
        if (!Set.of("true", "false").contains(text))
          throw new IllegalArgumentException("Invalid boolean");
        yield Boolean.valueOf(text);
      }
      case DATE -> {
        try {
          yield LocalDate.parse(text).toString();
        } catch (java.time.DateTimeException ex) {
          throw new IllegalArgumentException("Invalid ISO date", ex);
        }
      }
    };
  }

  public static List<Map<String, Object>> filters(Plan plan) {
    // Revalidate even if a provider is called directly rather than via a controller.
    validate(
        new Schema(plan.schemaId(), plan.schemaVersion(), plan.fields()),
        plan.tenant(),
        plan.constraints());
    List<Map<String, Object>> filters = new ArrayList<>();
    filters.add(Map.of("term", Map.of("tenantId", plan.tenant())));
    for (Constraint c : plan.constraints()) {
      Field field = plan.fields().get(c.field());
      Object val = value(field, c.value());
      Map<String, Object> clause;
      if (c.operator() == Operator.EQ || c.operator() == Operator.NE)
        clause = Map.of("term", Map.of(field.indexField(), val));
      else
        clause =
            Map.of(
                "range",
                Map.of(
                    field.indexField(),
                    Map.of(c.operator().name().toLowerCase(java.util.Locale.ROOT), val)));
      filters.add(
          c.operator() == Operator.NE
              ? Map.of(
                  "bool",
                  Map.of(
                      "filter",
                      List.of(Map.of("exists", Map.of("field", field.indexField()))),
                      "must_not",
                      List.of(clause)))
              : clause);
    }
    return List.copyOf(filters);
  }

  public static boolean matches(Plan plan, Map<String, Object> document) {
    if (!plan.tenant().equals(document.get("tenantId"))) return false;
    for (Constraint c : plan.constraints()) {
      Object actual = document.get(c.field());
      if (actual == null) return false;
      Field field = plan.fields().get(c.field());
      try {
        if (!accepts(field, value(field, actual.toString()), c)) return false;
      } catch (IllegalArgumentException ex) {
        return false;
      }
    }
    return true;
  }

  private static boolean accepts(Field field, Object actual, Constraint c) {
    int n = compare(actual, value(field, c.value()));
    return switch (c.operator()) {
      case EQ -> n == 0;
      case NE -> n != 0;
      case LT -> n < 0;
      case LTE -> n <= 0;
      case GT -> n > 0;
      case GTE -> n >= 0;
    };
  }

  private static int compare(Object a, Object b) {
    if (a instanceof BigDecimal n && b instanceof BigDecimal other) return n.compareTo(other);
    return a.toString().compareTo(b.toString());
  }
}
