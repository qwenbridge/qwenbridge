package io.qwenbridge.intelligence.schema;

import io.qwenbridge.intelligence.ir.QueryOperator;
import java.util.Objects;
import java.util.Set;

/** Allowlisted logical field definition, independent of a search provider. */
public record SchemaField(
    String name, Kind kind, Set<QueryOperator> operators, Set<String> currencies) {
  public enum Kind {
    ENTITY,
    DECIMAL
  }

  public SchemaField {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    operators = Set.copyOf(Objects.requireNonNull(operators, "operators"));
    currencies = Set.copyOf(Objects.requireNonNull(currencies, "currencies"));
    if (name.isBlank()
        || (kind == Kind.ENTITY && (!operators.isEmpty() || !currencies.isEmpty()))
        || (kind == Kind.DECIMAL && (operators.isEmpty() || currencies.isEmpty()))) {
      throw new IllegalArgumentException("invalid schema field");
    }
  }
}
