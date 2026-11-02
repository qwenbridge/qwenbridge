package io.qwenbridge.core.schema;

import static io.qwenbridge.core.queryir.Constraint.Operator.BETWEEN;
import static io.qwenbridge.core.queryir.Constraint.Operator.EQ;
import static io.qwenbridge.core.queryir.Constraint.Operator.GT;
import static io.qwenbridge.core.queryir.Constraint.Operator.GTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.IN;
import static io.qwenbridge.core.queryir.Constraint.Operator.LT;
import static io.qwenbridge.core.queryir.Constraint.Operator.LTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NOT_IN;

import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrJson;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Example schemas for three unrelated domains, written against the same contract. */
final class Schemas {

  private Schemas() {}

  static DomainSchema commerce(String version) {
    return new DomainSchema(
        "commerce",
        SchemaVersion.parse(version).orElseThrow(),
        Map.of(
            "price",
                FieldDefinition.of("price", FieldType.MONEY, Set.of(EQ, LT, LTE, GT, GTE, BETWEEN)),
            "color", FieldDefinition.of("color", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
            "material", FieldDefinition.of("material", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
            "category", FieldDefinition.of("category", FieldType.TEXT, Set.of(EQ, IN)),
            "connectivity", FieldDefinition.of("connectivity", FieldType.TEXT, Set.of(EQ, NE)),
            "inStock", FieldDefinition.of("inStock", FieldType.BOOLEAN, Set.of(EQ))),
        Set.of("category", "brand"),
        Set.of("search", "compare"));
  }

  static DomainSchema devops() {
    return new DomainSchema(
        "devops",
        new SchemaVersion(1, 0),
        Map.of(
            "memory_usage",
            new FieldDefinition(
                "memory_usage",
                FieldType.QUANTITY,
                Set.of(GT, GTE, LT, LTE),
                false,
                Optional.of("%"),
                Optional.of(BigDecimal.ZERO),
                Optional.of(new BigDecimal("100")),
                Set.of()),
            "observed_at",
            FieldDefinition.of("observed_at", FieldType.TEMPORAL, Set.of(GT, GTE, LT, LTE))),
        Set.of("kubernetes.pod", "service"),
        Set.of("investigate"));
  }

  static DomainSchema security() {
    return new DomainSchema(
        "security",
        new SchemaVersion(1, 0),
        Map.of(
            "bot_score",
            new FieldDefinition(
                "bot_score",
                FieldType.NUMBER,
                Set.of(LT, LTE, GT, GTE),
                false,
                Optional.empty(),
                Optional.of(BigDecimal.ZERO),
                Optional.of(new BigDecimal("99")),
                Set.of()),
            "source.trust",
            new FieldDefinition(
                "source.trust",
                FieldType.IDENTIFIER,
                Set.of(EQ, NE, IN, NOT_IN),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of("trusted", "untrusted", "unknown"))),
        Set.of("network.ip_address"),
        Set.of("analyze"));
  }

  static QueryIR fixture(String name) {
    try (InputStream in = Schemas.class.getResourceAsStream("/queryir/v1.0/" + name + ".json")) {
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return ((QueryIrJson.Result.Valid) QueryIrJson.read(json)).ir();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
