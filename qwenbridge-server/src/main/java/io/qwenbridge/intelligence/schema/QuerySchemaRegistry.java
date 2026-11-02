package io.qwenbridge.intelligence.schema;

import io.qwenbridge.intelligence.ir.QueryOperator;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** V1 logical allowlist. Not a physical index mapping or authorization policy. */
@ApplicationScoped
public class QuerySchemaRegistry {
  private static final Map<String, SchemaField> FIELDS =
      Map.of(
          "price",
              new SchemaField(
                  "price",
                  SchemaField.Kind.DECIMAL,
                  Set.of(QueryOperator.LESS_THAN, QueryOperator.LESS_THAN_OR_EQUAL),
                  Set.of("SEK", "EUR", "USD")),
          "BRAND", new SchemaField("BRAND", SchemaField.Kind.ENTITY, Set.of(), Set.of()),
          "PRODUCT", new SchemaField("PRODUCT", SchemaField.Kind.ENTITY, Set.of(), Set.of()));

  public Optional<SchemaField> field(String name) {
    return Optional.ofNullable(FIELDS.get(name));
  }

  public Map<String, SchemaField> fields() {
    return FIELDS;
  }
}
