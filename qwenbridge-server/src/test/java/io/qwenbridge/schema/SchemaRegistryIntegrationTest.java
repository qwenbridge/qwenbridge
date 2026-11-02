package io.qwenbridge.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.core.schema.SchemaRegistryException;
import io.qwenbridge.core.schema.SchemaValidator;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.core.schema.SchemaViolation;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/**
 * QB-047/049: the running application has a schema registry populated by the installed domain
 * packs, with no database configured at all.
 */
@QuarkusTest
class SchemaRegistryIntegrationTest {

  @Inject SchemaRegistry registry;

  @Test
  void testProfileHasNoDatabaseConfigured() {
    var config = ConfigProvider.getConfig();
    for (String name : config.getPropertyNames()) {
      assertThat(name).doesNotStartWith("quarkus.datasource");
      assertThat(name).doesNotStartWith("quarkus.mongodb");
    }
  }

  @Test
  void installedDomainPacksRegisteredTheirSchemasUnderTheirOwnOwner() {
    assertThat(registry.owner("commerce")).contains("pack:commerce");
    assertThat(registry.owner("library")).contains("pack:library");
    DomainSchema commerce = registry.latest("commerce").orElseThrow();
    assertThat(commerce.version()).isEqualTo(new SchemaVersion(1, 0));
    assertThat(commerce.fields().get("price").type()).isEqualTo(FieldType.NUMBER);
    assertThat(commerce.fields()).containsKey("inStock");
  }

  @Test
  void onePackCannotOverwriteOrRemoveAnotherPacksSchema() {
    DomainSchema library = registry.latest("library").orElseThrow();

    SchemaRegistryException removal =
        org.junit.jupiter.api.Assertions.assertThrows(
            SchemaRegistryException.class,
            () -> registry.unregister("pack:commerce", library.key()));
    assertThat(removal.code()).isEqualTo(SchemaRegistryException.Code.OWNERSHIP_VIOLATION);

    DomainSchema hijack =
        new DomainSchema(
            "library",
            new SchemaVersion(library.version().major(), library.version().minor() + 1),
            library.fields(),
            library.entityTypes(),
            library.capabilities());
    SchemaRegistryException overwrite =
        org.junit.jupiter.api.Assertions.assertThrows(
            SchemaRegistryException.class, () -> registry.register("pack:commerce", hijack));
    assertThat(overwrite.code()).isEqualTo(SchemaRegistryException.Code.OWNERSHIP_VIOLATION);
    assertThat(registry.latest("library")).contains(library);
  }

  @Test
  void queryIrIsCheckedAgainstTheRegisteredPackSchema() {
    String text = "black shoes under 100";
    QueryIR ir =
        new QueryIR(
            io.qwenbridge.core.queryir.IrVersion.CURRENT,
            Optional.empty(),
            text,
            LanguageTag.parse("en").orElseThrow(),
            List.of(),
            List.of(),
            List.of(
                new Constraint(
                    "c1",
                    "price",
                    Constraint.Operator.LT,
                    Optional.of(new IrValue.Number(new BigDecimal("100"))),
                    Constraint.Strength.HARD,
                    Constraint.Provenance.USER_TEXT,
                    "explicit bound",
                    Optional.of(Span.of(text, 12, 21)),
                    Confidence.deterministic(),
                    Optional.empty()),
                new Constraint(
                    "c2",
                    "warranty_years",
                    Constraint.Operator.GT,
                    Optional.of(new IrValue.Number(BigDecimal.ONE)),
                    Constraint.Strength.SOFT,
                    Constraint.Provenance.INFERRED,
                    "model suggestion",
                    Optional.empty(),
                    Confidence.modelDecision(),
                    Optional.empty())),
            List.of(),
            RetrievalHints.NONE,
            List.of(),
            Map.of());
    assertThat(QueryIrValidator.validate(ir)).isEmpty();

    List<SchemaViolation> violations =
        SchemaValidator.validate(ir, registry.latest("commerce").orElseThrow());

    assertThat(violations)
        .extracting(SchemaViolation::code)
        .containsExactly(SchemaViolation.Code.UNKNOWN_FIELD);
    assertThat(violations.getFirst().path()).isEqualTo("/constraints/1/field");
  }
}
