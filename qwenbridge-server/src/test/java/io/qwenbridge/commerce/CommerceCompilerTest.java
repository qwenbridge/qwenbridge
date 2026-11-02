package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;
import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.analysis.model.Grounded.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommerceCompilerTest {
  private Constraint c(String field, Operator operator, String value) {
    return new Constraint(field, operator, value, new Evidence("original", 0, 1, "x"));
  }

  @Test
  void onlyProductsMeetingEveryConstraintAndTenantMatch() {
    var plan =
        CommerceCompiler.validate(
            CommerceSchemaRegistry.defaults(),
            "shopA",
            List.of(
                c("brand", Operator.EQ, "Nike"),
                c("color", Operator.EQ, "black"),
                c("size", Operator.EQ, "42"),
                c("price", Operator.LT, "100"),
                c("currency", Operator.EQ, "EUR"),
                c("inStock", Operator.EQ, "true"),
                c("condition", Operator.NE, "refurbished")));
    Map<String, Object> good =
        Map.of(
            "tenantId",
            "shopA",
            "brand",
            "Nike",
            "color",
            "black",
            "size",
            "42",
            "price",
            99.99,
            "currency",
            "EUR",
            "inStock",
            true,
            "condition",
            "new");
    assertThat(CommerceCompiler.matches(plan, good)).isTrue();
    for (var change :
        Map.<String, Object>of(
                "tenantId",
                "shopB",
                "brand",
                "Adidas",
                "color",
                "red",
                "size",
                "41",
                "price",
                100,
                "currency",
                "USD",
                "inStock",
                false,
                "condition",
                "refurbished")
            .entrySet()) {
      var bad = new java.util.HashMap<>(good);
      bad.put(change.getKey(), change.getValue());
      assertThat(CommerceCompiler.matches(plan, bad)).as(change.getKey()).isFalse();
    }
    assertThat(CommerceCompiler.filters(plan)).hasSize(8);
  }

  @Test
  void rejectsUnsupportedFieldsCurrenciesOperatorsAndContradictions() {
    var schema = CommerceSchemaRegistry.defaults();
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema, "public", List.of(c("tenantId", Operator.EQ, "other"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema, "public", List.of(c("inStock", Operator.LT, "true"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema, "public", List.of(c("price", Operator.LT, "100"))))
        .hasMessageContaining("currency");
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema,
                    "public",
                    List.of(c("currency", Operator.EQ, "EUR"), c("currency", Operator.EQ, "USD"))))
        .hasMessageContaining("Conflicting");
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema,
                    "public",
                    List.of(
                        c("price", Operator.GTE, "100"),
                        c("price", Operator.LT, "100"),
                        c("currency", Operator.EQ, "EUR"))))
        .hasMessageContaining("Empty range");
    assertThatThrownBy(
            () ->
                CommerceCompiler.validate(
                    schema, "public", List.of(c("inStock", Operator.EQ, "yes"))))
        .hasMessageContaining("boolean");
  }

  @Test
  void versionsAreImmutableAndTenantsCannotReadEachOthersSchemas() {
    var registry = new CommerceSchemaRegistry();
    var schema =
        new Schema(
            "custom", 2, Map.of("price", CommerceSchemaRegistry.defaults().fields().get("price")));
    registry.register("shopA", schema);
    assertThat(registry.get("shopA", "custom", 2)).isEqualTo(schema);
    assertThatThrownBy(() -> registry.get("shopB", "custom", 2)).hasMessageContaining("Unknown");
    assertThatThrownBy(
            () ->
                registry.register(
                    "shopA", new Schema("custom", 2, CommerceSchemaRegistry.defaults().fields())))
        .hasMessageContaining("immutable");
    assertThat(registry.get("public", "commerce", 1).fields()).doesNotContainKey("tenantId");
  }
}
