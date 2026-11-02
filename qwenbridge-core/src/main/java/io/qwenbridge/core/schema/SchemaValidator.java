package io.qwenbridge.core.schema;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Entity;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.schema.SchemaViolation.Code;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks a structurally valid {@link QueryIR} against a {@link DomainSchema} (QB-045, QB-048).
 *
 * <ul>
 *   <li>Every constraint field must exist in the schema; an unknown field is reported and must not
 *       be executed, whatever produced it (a model cannot invent fields).
 *   <li>Operators, value types, quantity units, numeric bounds and closed vocabularies must fit the
 *       field definition.
 *   <li>Entity types must be declared by the schema.
 *   <li>Semantic signals are never executable and are not checked here; {@code extensions} are
 *       domain payloads validated by their owning pack.
 * </ul>
 *
 * Messages describe the rule and never echo user values.
 */
public final class SchemaValidator {

  private SchemaValidator() {}

  public static List<SchemaViolation> validate(QueryIR ir, DomainSchema schema) {
    List<SchemaViolation> violations = new ArrayList<>();
    for (int i = 0; i < ir.constraints().size(); i++) {
      constraint(violations, "/constraints/" + i, ir.constraints().get(i), schema);
    }
    for (int i = 0; i < ir.entities().size(); i++) {
      Entity entity = ir.entities().get(i);
      if (!schema.entityTypes().contains(entity.type())) {
        violations.add(
            new SchemaViolation(
                "/entities/" + i + "/type",
                Code.UNKNOWN_ENTITY_TYPE,
                "not declared by schema " + schema.key()));
      }
    }
    return List.copyOf(violations);
  }

  private static void constraint(
      List<SchemaViolation> violations, String path, Constraint constraint, DomainSchema schema) {
    FieldDefinition field = schema.fields().get(constraint.field());
    if (field == null) {
      violations.add(
          new SchemaViolation(
              path + "/field", Code.UNKNOWN_FIELD, "not a field of schema " + schema.key()));
      return;
    }
    if (!field.operators().contains(constraint.operator())) {
      violations.add(
          new SchemaViolation(
              path + "/operator",
              Code.OPERATOR_NOT_ALLOWED,
              constraint.operator() + " is not allowed on this field"));
    }
    constraint.value().ifPresent(value -> value(violations, path + "/value", value, field));
  }

  private static void value(
      List<SchemaViolation> violations, String path, IrValue value, FieldDefinition field) {
    switch (value) {
      case IrValue.Range range -> {
        range.min().ifPresent(min -> value(violations, path + "/min", min, field));
        range.max().ifPresent(max -> value(violations, path + "/max", max, field));
      }
      case IrValue.ListOf list -> {
        for (int i = 0; i < list.values().size(); i++) {
          value(violations, path + "/values/" + i, list.values().get(i), field);
        }
      }
      default -> scalar(violations, path, value, field);
    }
  }

  private static void scalar(
      List<SchemaViolation> violations, String path, IrValue value, FieldDefinition field) {
    if (!field.type().accepts(value)) {
      violations.add(
          new SchemaViolation(
              path,
              Code.TYPE_MISMATCH,
              value.type() + " does not fit a " + field.type() + " field"));
      return;
    }
    if (value instanceof IrValue.Quantity quantity
        && !field.unit().orElse("").equals(quantity.unit())) {
      violations.add(
          new SchemaViolation(
              path + "/unit", Code.UNIT_MISMATCH, "field unit is " + field.unit().orElse("")));
      return;
    }
    numeric(value)
        .ifPresent(
            number -> {
              boolean below = field.min().filter(min -> number.compareTo(min) < 0).isPresent();
              boolean above = field.max().filter(max -> number.compareTo(max) > 0).isPresent();
              if (below || above) {
                violations.add(
                    new SchemaViolation(path, Code.OUT_OF_RANGE, "outside the field's bounds"));
              }
            });
    if (value instanceof IrValue.Text text
        && !field.allowedValues().isEmpty()
        && !field.allowedValues().contains(text.value())) {
      violations.add(
          new SchemaViolation(path, Code.VALUE_NOT_ALLOWED, "not in the field's vocabulary"));
    }
  }

  private static Optional<BigDecimal> numeric(IrValue value) {
    return switch (value) {
      case IrValue.Number n -> Optional.of(n.value());
      case IrValue.Quantity q -> Optional.of(q.value());
      case IrValue.Money m -> Optional.of(m.amount());
      default -> Optional.empty();
    };
  }
}
