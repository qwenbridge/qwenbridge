package io.qwenbridge.schema;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.pack.DomainPack;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Converts a plugin-SDK {@link DomainPack.Definition} into the Core {@link DomainSchema} (QB-044,
 * QB-047). Only meaning is carried over; backend details ({@code backendField}, {@code caseFold})
 * stay with the pack and its connectors and never enter the Core schema.
 */
public final class DomainPackSchemas {

  private DomainPackSchemas() {}

  public static String owner(DomainPack.Definition definition) {
    return "pack:" + definition.id();
  }

  public static DomainSchema toSchema(DomainPack.Definition definition) {
    Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    definition
        .fields()
        .forEach(
            (name, field) ->
                fields.put(
                    name,
                    FieldDefinition.of(name, type(field.type()), operators(field.operators()))));
    return new DomainSchema(
        definition.id(),
        new SchemaVersion(definition.schemaVersion(), 0),
        fields,
        Set.of(),
        Set.of("search"));
  }

  static FieldType type(DomainPack.Type type) {
    return switch (type) {
      case TEXT -> FieldType.TEXT;
      case DECIMAL -> FieldType.NUMBER;
      case BOOLEAN -> FieldType.BOOLEAN;
      case DATE -> FieldType.TEMPORAL;
    };
  }

  static Set<Constraint.Operator> operators(Set<DomainPack.Operator> operators) {
    EnumSet<Constraint.Operator> mapped = EnumSet.noneOf(Constraint.Operator.class);
    operators.forEach(op -> mapped.add(Constraint.Operator.valueOf(op.name())));
    return mapped;
  }
}
