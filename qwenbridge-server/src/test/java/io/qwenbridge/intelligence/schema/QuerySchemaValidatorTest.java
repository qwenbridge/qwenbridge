package io.qwenbridge.intelligence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryEntity;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.ir.QueryOperator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuerySchemaValidatorTest {
  private final QuerySchemaRegistry registry = new QuerySchemaRegistry();
  private final QuerySchemaValidator validator = new QuerySchemaValidator(registry);

  @Test
  void acceptsKnownEntityAndPriceConstraint() {
    String query = "Apple under 1200 SEK";
    QueryIR ir =
        new QueryIR(
            1,
            query,
            List.of(new QueryEntity("Apple", "BRAND", 0, 5)),
            List.of(
                new QueryConstraint(
                    "price",
                    QueryOperator.LESS_THAN,
                    new BigDecimal("1200"),
                    "SEK",
                    6,
                    query.length())));
    assertThat(validator.validate(ir).valid()).isTrue();
  }

  @Test
  void rejectsUnknownEntityType() {
    QueryIR ir =
        new QueryIR(1, "Apple", List.of(new QueryEntity("Apple", "ADMIN", 0, 5)), List.of());
    assertThat(validator.validate(ir).errors()).containsExactly("unsupported entity type: ADMIN");
  }

  @Test
  void rejectsUnknownConstraintField() {
    QueryIR ir =
        new QueryIR(
            1,
            "x",
            List.of(),
            List.of(
                new QueryConstraint(
                    "internal_secret", QueryOperator.LESS_THAN, BigDecimal.ONE, "USD", 0, 1)));
    assertThat(validator.validate(ir).errors())
        .containsExactly("unsupported constraint field: internal_secret");
  }

  @Test
  void rejectsUnsupportedCurrencyWithoutConversion() {
    QueryIR ir =
        new QueryIR(
            1,
            "x",
            List.of(),
            List.of(
                new QueryConstraint(
                    "price", QueryOperator.LESS_THAN_OR_EQUAL, BigDecimal.ONE, "GBP", 0, 1)));
    assertThat(validator.validate(ir).errors())
        .containsExactly("unsupported currency for field: price");
  }

  @Test
  void registryIsImmutableAndCaseSensitive() {
    assertThat(registry.field("price")).isPresent();
    assertThat(registry.field("Price")).isEmpty();
    assertThatThrownBy(() -> registry.fields().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void returnsImmutableValidationErrors() {
    QueryIR ir = new QueryIR(1, "x", List.of(new QueryEntity("x", "UNKNOWN", 0, 1)), List.of());
    var result = validator.validate(ir);
    assertThat(result.valid()).isFalse();
    assertThatThrownBy(() -> result.errors().add("oops"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void legacyAllowlistIsValidatedThroughTheCoreGate() {
    var schema = validator.coreSchema();
    assertThat(schema.fields().get("price").type()).isEqualTo(FieldType.NUMBER);
    assertThat(schema.fields().get("price.currency").allowedValues())
        .containsExactlyInAnyOrder("SEK", "EUR", "USD");
  }

  @Test
  void reportsOperatorAndCurrencyViolationsFromTheCoreGate() {
    QuerySchemaRegistry strict =
        new QuerySchemaRegistry() {
          private final Map<String, SchemaField> fields =
              Map.of(
                  "price",
                  new SchemaField(
                      "price",
                      SchemaField.Kind.DECIMAL,
                      Set.of(QueryOperator.LESS_THAN),
                      Set.of("SEK")));

          @Override
          public Optional<SchemaField> field(String name) {
            return Optional.ofNullable(fields.get(name));
          }

          @Override
          public Map<String, SchemaField> fields() {
            return fields;
          }
        };
    QueryIR ir =
        new QueryIR(
            1,
            "max 5 EUR",
            List.of(),
            List.of(
                new QueryConstraint(
                    "price", QueryOperator.LESS_THAN_OR_EQUAL, new BigDecimal("5"), "EUR", 0, 9)));

    assertThat(new QuerySchemaValidator(strict).validate(ir).errors())
        .containsExactly(
            "unsupported operator for field: price", "unsupported currency for field: price");
  }

  @Test
  void rejectsFieldsOutsideTheCoreNamingRule() {
    QueryIR ir =
        new QueryIR(
            1,
            "x",
            List.of(),
            List.of(
                new QueryConstraint(
                    "Price", QueryOperator.LESS_THAN, BigDecimal.ONE, "SEK", 0, 1)));
    assertThat(validator.validate(ir).errors())
        .containsExactly("unsupported constraint field: Price");
  }
}
