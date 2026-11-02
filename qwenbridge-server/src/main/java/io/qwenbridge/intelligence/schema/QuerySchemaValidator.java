package io.qwenbridge.intelligence.schema;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Provenance;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.IrVersion;
import io.qwenbridge.core.queryir.Names;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.core.schema.SchemaViolation;
import io.qwenbridge.core.validation.ConstraintAssessment;
import io.qwenbridge.core.validation.ConstraintValidator;
import io.qwenbridge.core.validation.DomainMapping;
import io.qwenbridge.core.validation.MappingRule;
import io.qwenbridge.core.validation.ValidationReport;
import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryEntity;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.ir.QueryOperator;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fail-closed validation of logical fields before planning or execution.
 *
 * <p>Since QB-056 constraint checks run through the single Core validation gate ({@link
 * ConstraintValidator}): the legacy allowlist is expressed as a Core {@link DomainSchema} in which
 * a DECIMAL field is a NUMBER field plus a closed {@code <field>.currency} vocabulary, joined by an
 * approved {@link MappingRule.MoneyToNumberAndCurrency} rule. The result type and messages are
 * unchanged for existing callers. Entity types stay on the legacy allowlist, whose upper-case names
 * predate the Core naming rule.
 */
@ApplicationScoped
public class QuerySchemaValidator {
  static final String SCHEMA_ID = "legacy-price";

  private final QuerySchemaRegistry registry;
  private final DomainSchema schema;
  private final DomainMapping mapping;

  public QuerySchemaValidator(QuerySchemaRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
    Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    List<MappingRule> rules = new ArrayList<>();
    registry.fields().values().stream()
        .filter(field -> field.kind() == SchemaField.Kind.DECIMAL)
        .filter(field -> Names.valid(field.name()))
        .forEach(
            field -> {
              String currencyField = field.name() + ".currency";
              fields.put(
                  field.name(),
                  FieldDefinition.of(field.name(), FieldType.NUMBER, operators(field.operators())));
              fields.put(
                  currencyField,
                  new FieldDefinition(
                      currencyField,
                      FieldType.IDENTIFIER,
                      Set.of(Constraint.Operator.EQ),
                      false,
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      field.currencies()));
              rules.add(
                  new MappingRule.MoneyToNumberAndCurrency(
                      "legacy-" + field.name() + "-money", field.name(), currencyField));
            });
    this.schema =
        new DomainSchema(SCHEMA_ID, new SchemaVersion(1, 0), fields, Set.of(), Set.of("search"));
    this.mapping = new DomainMapping(SCHEMA_ID, rules);
  }

  public ValidationResult validate(QueryIR query) {
    Objects.requireNonNull(query, "query");
    List<String> errors = new ArrayList<>();
    for (QueryEntity entity : query.entities()) {
      SchemaField field = registry.field(entity.type()).orElse(null);
      if (field == null || field.kind() != SchemaField.Kind.ENTITY) {
        errors.add("unsupported entity type: " + entity.type());
      }
    }
    for (QueryConstraint constraint : query.constraints()) {
      errors.addAll(constraintErrors(query.originalQuery(), constraint));
    }
    return new ValidationResult(errors.isEmpty(), errors);
  }

  /** Each legacy constraint is checked on its own, as before; combinations are not judged here. */
  private List<String> constraintErrors(String text, QueryConstraint constraint) {
    String field = constraint.field();
    if (!Names.valid(field) || !schema.fields().containsKey(field)) {
      return List.of("unsupported constraint field: " + field);
    }
    Constraint core =
        new Constraint(
            "c1",
            field,
            operator(constraint.operator()),
            Optional.of(new IrValue.Money(constraint.value(), Optional.of(constraint.currency()))),
            Strength.HARD,
            Provenance.USER_TEXT,
            "legacy structured constraint",
            Optional.of(Span.of(text, constraint.start(), constraint.end())),
            Confidence.deterministic(),
            Optional.empty());
    io.qwenbridge.core.queryir.QueryIR ir =
        new io.qwenbridge.core.queryir.QueryIR(
            IrVersion.CURRENT,
            Optional.empty(),
            text,
            LanguageTag.UNDETERMINED,
            List.of(),
            List.of(),
            List.of(core),
            List.of(),
            RetrievalHints.NONE,
            List.of(),
            Map.of());
    ValidationReport report = ConstraintValidator.validate(ir, schema, mapping);
    if (!report.blocking()) {
      return List.of();
    }
    List<String> errors = new ArrayList<>();
    for (ConstraintAssessment assessment : report.assessments()) {
      for (SchemaViolation violation : assessment.violations()) {
        String message =
            switch (violation.code()) {
              case OPERATOR_NOT_ALLOWED -> "unsupported operator for field: " + field;
              case VALUE_NOT_ALLOWED -> "unsupported currency for field: " + field;
              case UNKNOWN_FIELD -> "unsupported constraint field: " + field;
              default -> "invalid constraint for field: " + field;
            };
        if (!errors.contains(message)) {
          errors.add(message);
        }
      }
    }
    if (errors.isEmpty()) {
      // Blocking without a schema violation (e.g. a structural defect): never pass silently.
      errors.add("invalid constraint for field: " + field);
    }
    return errors;
  }

  static Constraint.Operator operator(QueryOperator operator) {
    return switch (operator) {
      case LESS_THAN -> Constraint.Operator.LT;
      case LESS_THAN_OR_EQUAL -> Constraint.Operator.LTE;
    };
  }

  private static Set<Constraint.Operator> operators(Set<QueryOperator> operators) {
    EnumSet<Constraint.Operator> mapped = EnumSet.noneOf(Constraint.Operator.class);
    operators.forEach(op -> mapped.add(operator(op)));
    return mapped;
  }

  /** The Core schema the legacy allowlist is validated against. */
  public DomainSchema coreSchema() {
    return schema;
  }

  public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
      errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
      if (valid != errors.isEmpty()) {
        throw new IllegalArgumentException("valid flag must agree with errors");
      }
    }
  }
}
