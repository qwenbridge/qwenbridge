package io.qwenbridge.core.queryir;

import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrViolation.Code;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic validation of a {@link QueryIR} (QB-038, QB-039, QB-041). Collects every violation; an
 * empty result means the IR may proceed to schema mapping and planning.
 */
public final class QueryIrValidator {

  public static final int MAX_TEXT_LENGTH = 8192;
  public static final int MAX_INTENTS = 16;
  public static final int MAX_ENTITIES = 64;
  public static final int MAX_CONSTRAINTS = 64;
  public static final int MAX_SIGNALS = 32;
  public static final int MAX_SUBQUERIES = 16;
  public static final int MAX_EXTENSIONS = 8;
  public static final int MAX_LIST_VALUES = 64;
  public static final int MAX_EXTENSION_DEPTH = 8;

  static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$");
  static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");

  private QueryIrValidator() {}

  public static List<IrViolation> validate(QueryIR ir) {
    List<IrViolation> v = new ArrayList<>();
    if (!IrVersion.READABLE.canRead(ir.version())) {
      v.add(
          new IrViolation(
              "/irVersion",
              Code.UNSUPPORTED_VERSION,
              "supported: " + IrVersion.CURRENT + " to " + IrVersion.READABLE));
    }
    String text = ir.originalText();
    if (text.isBlank()) {
      v.add(new IrViolation("/originalText", Code.REQUIRED, "must not be blank"));
    } else if (text.length() > MAX_TEXT_LENGTH) {
      v.add(new IrViolation("/originalText", Code.LIMIT_EXCEEDED, "too long"));
    }
    ir.queryId()
        .filter(id -> !ID.matcher(id).matches())
        .ifPresent(
            id -> v.add(new IrViolation("/queryId", Code.INVALID_FORMAT, "invalid identifier")));

    limit(v, "/intents", ir.intents().size(), MAX_INTENTS);
    limit(v, "/entities", ir.entities().size(), MAX_ENTITIES);
    limit(v, "/constraints", ir.constraints().size(), MAX_CONSTRAINTS);
    limit(v, "/semanticSignals", ir.semanticSignals().size(), MAX_SIGNALS);
    limit(v, "/subqueries", ir.subqueries().size(), MAX_SUBQUERIES);
    limit(v, "/extensions", ir.extensions().size(), MAX_EXTENSIONS);

    Set<String> intentIds = new HashSet<>();
    for (int i = 0; i < ir.intents().size(); i++) {
      Intent intent = ir.intents().get(i);
      String path = "/intents/" + i;
      id(v, path, intent.id(), intentIds);
      name(v, path + "/type", intent.type());
      intent.span().ifPresent(span -> span(v, path + "/span", span, text));
    }

    Set<String> entityIds = new HashSet<>();
    for (int i = 0; i < ir.entities().size(); i++) {
      Entity entity = ir.entities().get(i);
      String path = "/entities/" + i;
      id(v, path, entity.id(), entityIds);
      name(v, path + "/type", entity.type());
      if (entity.value().isBlank()) {
        v.add(new IrViolation(path + "/value", Code.REQUIRED, "must not be blank"));
      }
      span(v, path + "/span", entity.span(), text);
    }

    Set<String> subqueryIds = new HashSet<>();
    ir.subqueries().forEach(s -> subqueryIds.add(s.id()));

    Set<String> constraintIds = new HashSet<>();
    for (int i = 0; i < ir.constraints().size(); i++) {
      Constraint c = ir.constraints().get(i);
      String path = "/constraints/" + i;
      id(v, path, c.id(), constraintIds);
      name(v, path + "/field", c.field());
      if (c.reason().isBlank()) {
        v.add(
            new IrViolation(
                path + "/reason", Code.REQUIRED, "classification reason must be recorded"));
      }
      if (c.provenance() == Constraint.Provenance.USER_TEXT && c.span().isEmpty()) {
        v.add(
            new IrViolation(
                path + "/span", Code.REQUIRED, "user-stated constraints need evidence"));
      }
      if (c.provenance() == Constraint.Provenance.INFERRED
          && c.strength() == Constraint.Strength.HARD) {
        v.add(
            new IrViolation(
                path + "/strength", Code.INVALID_VALUE, "an inferred constraint cannot be HARD"));
      }
      c.span().ifPresent(span -> span(v, path + "/span", span, text));
      c.scope()
          .filter(scope -> !subqueryIds.contains(scope))
          .ifPresent(
              scope ->
                  v.add(
                      new IrViolation(
                          path + "/scope", Code.UNKNOWN_REFERENCE, "no such subquery")));
      operatorValue(v, path, c.operator(), c.value());
    }

    Set<String> signalIds = new HashSet<>();
    for (int i = 0; i < ir.semanticSignals().size(); i++) {
      SemanticSignal s = ir.semanticSignals().get(i);
      String path = "/semanticSignals/" + i;
      id(v, path, s.id(), signalIds);
      name(v, path + "/concept", s.concept());
      s.target().ifPresent(target -> name(v, path + "/target", target));
      s.span().ifPresent(span -> span(v, path + "/span", span, text));
    }

    validateSubqueries(v, ir, intentIds, constraintIds);

    ir.extensions()
        .forEach(
            (namespace, extension) -> {
              String path = "/extensions/" + namespace;
              if (!NAMESPACE.matcher(namespace).matches()) {
                v.add(new IrViolation(path, Code.INVALID_FORMAT, "invalid namespace"));
              }
              if (IrVersion.parse(extension.version()).isEmpty()) {
                v.add(
                    new IrViolation(path + "/version", Code.INVALID_FORMAT, "must be major.minor"));
              }
              if (depth(extension.data(), 1) > MAX_EXTENSION_DEPTH) {
                v.add(new IrViolation(path + "/data", Code.LIMIT_EXCEEDED, "nested too deeply"));
              }
            });
    return List.copyOf(v);
  }

  private static void validateSubqueries(
      List<IrViolation> v, QueryIR ir, Set<String> intentIds, Set<String> constraintIds) {
    Set<String> ids = new HashSet<>();
    Map<String, List<String>> edges = new HashMap<>();
    for (int i = 0; i < ir.subqueries().size(); i++) {
      SubQuery s = ir.subqueries().get(i);
      String path = "/subqueries/" + i;
      id(v, path, s.id(), ids);
      if (s.objective().isBlank()) {
        v.add(new IrViolation(path + "/objective", Code.REQUIRED, "must not be blank"));
      }
      s.intentIds().stream()
          .filter(ref -> !intentIds.contains(ref))
          .forEach(
              ref ->
                  v.add(
                      new IrViolation(
                          path + "/intentIds", Code.UNKNOWN_REFERENCE, "no such intent")));
      s.constraintIds().stream()
          .filter(ref -> !constraintIds.contains(ref))
          .forEach(
              ref ->
                  v.add(
                      new IrViolation(
                          path + "/constraintIds", Code.UNKNOWN_REFERENCE, "no such constraint")));
      edges.put(s.id(), s.dependsOn());
    }
    for (int i = 0; i < ir.subqueries().size(); i++) {
      SubQuery s = ir.subqueries().get(i);
      for (String dependency : s.dependsOn()) {
        if (!edges.containsKey(dependency)) {
          v.add(
              new IrViolation(
                  "/subqueries/" + i + "/dependsOn", Code.UNKNOWN_REFERENCE, "no such subquery"));
        }
      }
    }
    if (hasCycle(edges)) {
      v.add(new IrViolation("/subqueries", Code.CYCLE, "dependencies must be acyclic"));
    }
  }

  private static boolean hasCycle(Map<String, List<String>> edges) {
    Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done
    for (String node : edges.keySet()) {
      if (visit(node, edges, state)) {
        return true;
      }
    }
    return false;
  }

  private static boolean visit(
      String node, Map<String, List<String>> edges, Map<String, Integer> state) {
    Integer s = state.get(node);
    if (s != null) {
      return s == 1;
    }
    state.put(node, 1);
    for (String next : edges.getOrDefault(node, List.of())) {
      if (edges.containsKey(next) && visit(next, edges, state)) {
        return true;
      }
    }
    state.put(node, 2);
    return false;
  }

  private static void operatorValue(
      List<IrViolation> v, String path, Operator operator, Optional<IrValue> value) {
    String at = path + "/value";
    if (operator.valueless()) {
      if (value.isPresent()) {
        v.add(new IrViolation(at, Code.OPERATOR_VALUE_MISMATCH, operator + " takes no value"));
      }
      return;
    }
    if (value.isEmpty()) {
      v.add(new IrViolation(at, Code.REQUIRED, operator + " needs a value"));
      return;
    }
    IrValue val = value.get();
    value(v, at, val);
    boolean ok =
        switch (operator) {
          case BETWEEN -> val instanceof IrValue.Range;
          case IN, NOT_IN -> val instanceof IrValue.ListOf list && !list.values().isEmpty();
          case LT, LTE, GT, GTE -> orderable(val);
          case CONTAINS, NOT_CONTAINS -> val instanceof IrValue.Text;
          case EQ, NE -> !(val instanceof IrValue.Range) && !(val instanceof IrValue.ListOf);
          case EXISTS, NOT_EXISTS -> false;
        };
    if (!ok) {
      v.add(
          new IrViolation(
              at, Code.OPERATOR_VALUE_MISMATCH, operator + " does not accept " + val.type()));
    }
  }

  private static boolean orderable(IrValue value) {
    return value instanceof IrValue.Number
        || value instanceof IrValue.Quantity
        || value instanceof IrValue.Money
        || (value instanceof IrValue.Temporal t && t.kind() != IrValue.Temporal.Kind.DURATION);
  }

  private static void value(List<IrViolation> v, String path, IrValue value) {
    switch (value) {
      case IrValue.Temporal t -> temporal(v, path, t);
      case IrValue.Range r -> {
        r.min().ifPresent(min -> value(v, path + "/min", min));
        r.max().ifPresent(max -> value(v, path + "/max", max));
        if (r.min().isPresent() && r.max().isPresent()) {
          IrValue min = r.min().get();
          IrValue max = r.max().get();
          if (!min.type().equals(max.type()) || !orderable(min)) {
            v.add(
                new IrViolation(
                    path, Code.INVALID_VALUE, "range bounds must be one orderable type"));
          } else if (compare(min, max).filter(c -> c > 0).isPresent()) {
            v.add(new IrViolation(path, Code.INVALID_VALUE, "min must not exceed max"));
          }
        } else {
          IrValue bound = r.min().orElseGet(() -> r.max().orElseThrow());
          if (!orderable(bound)) {
            v.add(new IrViolation(path, Code.INVALID_VALUE, "range bound must be orderable"));
          }
        }
      }
      case IrValue.ListOf list -> {
        if (list.values().size() > MAX_LIST_VALUES) {
          v.add(new IrViolation(path, Code.LIMIT_EXCEEDED, "too many values"));
        }
        if (list.values().stream().map(IrValue::type).distinct().count() > 1) {
          v.add(new IrViolation(path, Code.INVALID_VALUE, "list values must share one type"));
        }
        for (int i = 0; i < list.values().size(); i++) {
          IrValue item = list.values().get(i);
          if (item instanceof IrValue.ListOf || item instanceof IrValue.Range) {
            v.add(
                new IrViolation(
                    path + "/values/" + i, Code.INVALID_VALUE, "lists hold scalar values"));
          } else {
            value(v, path + "/values/" + i, item);
          }
        }
      }
      default -> {}
    }
  }

  private static Optional<Integer> compare(IrValue a, IrValue b) {
    if (a instanceof IrValue.Number x && b instanceof IrValue.Number y) {
      return Optional.of(x.value().compareTo(y.value()));
    }
    if (a instanceof IrValue.Quantity x
        && b instanceof IrValue.Quantity y
        && x.unit().equals(y.unit())) {
      return Optional.of(x.value().compareTo(y.value()));
    }
    if (a instanceof IrValue.Money x
        && b instanceof IrValue.Money y
        && x.currency().equals(y.currency())) {
      return Optional.of(x.amount().compareTo(y.amount()));
    }
    return Optional.empty();
  }

  private static void temporal(List<IrViolation> v, String path, IrValue.Temporal t) {
    try {
      switch (t.kind()) {
        case INSTANT -> Instant.parse(t.value());
        case DATE -> LocalDate.parse(t.value());
        case DURATION, RELATIVE_PAST -> {
          Duration duration = Duration.parse(t.value());
          if (duration.isNegative() || duration.isZero()) {
            v.add(new IrViolation(path, Code.INVALID_VALUE, "duration must be positive"));
          }
        }
      }
      t.reference().ifPresent(Instant::parse);
    } catch (DateTimeException e) {
      v.add(new IrViolation(path, Code.INVALID_VALUE, "not valid ISO-8601 for " + t.kind()));
    }
  }

  private static void id(List<IrViolation> v, String path, String id, Set<String> seen) {
    if (!ID.matcher(id).matches()) {
      v.add(new IrViolation(path + "/id", Code.INVALID_FORMAT, "invalid identifier"));
    } else if (!seen.add(id)) {
      v.add(new IrViolation(path + "/id", Code.DUPLICATE_ID, "identifier already used"));
    }
  }

  private static void name(List<IrViolation> v, String path, String name) {
    if (!Names.valid(name)) {
      v.add(
          new IrViolation(
              path, Code.INVALID_FORMAT, "must be a dotted name of lowercase-initial segments"));
    }
  }

  private static void span(List<IrViolation> v, String path, Span span, String text) {
    if (!span.matches(text)) {
      v.add(new IrViolation(path, Code.INVALID_SPAN, "does not match the original text"));
    }
  }

  private static void limit(List<IrViolation> v, String path, int size, int max) {
    if (size > max) {
      v.add(new IrViolation(path, Code.LIMIT_EXCEEDED, "at most " + max + " entries"));
    }
  }

  private static int depth(Object value, int level) {
    if (value instanceof Map<?, ?> map) {
      return map.values().stream().mapToInt(x -> depth(x, level + 1)).max().orElse(level);
    }
    if (value instanceof List<?> list) {
      return list.stream().mapToInt(x -> depth(x, level + 1)).max().orElse(level);
    }
    return level;
  }
}
