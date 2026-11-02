package io.qwenbridge.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.schema.SchemaViolation.Code;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** QB-045, QB-048: QueryIR constraints are checked against the active domain schema. */
class SchemaValidatorTest {

  @Test
  void goldenFixturesFitTheirDomainSchemas() {
    assertEquals(
        List.of(),
        SchemaValidator.validate(Schemas.fixture("commerce-filterless"), Schemas.commerce("1.0")));
    assertEquals(
        List.of(), SchemaValidator.validate(Schemas.fixture("devops-pods"), Schemas.devops()));
    assertEquals(
        List.of(), SchemaValidator.validate(Schemas.fixture("security-ips"), Schemas.security()));
    assertEquals(
        List.of(),
        SchemaValidator.validate(Schemas.fixture("multi-step"), Schemas.commerce("1.0")));
  }

  @Test
  void aQueryForOneDomainDoesNotFitAnotherDomain() {
    List<Code> codes =
        SchemaValidator.validate(Schemas.fixture("devops-pods"), Schemas.commerce("1.0")).stream()
            .map(SchemaViolation::code)
            .toList();
    assertTrue(codes.contains(Code.UNKNOWN_FIELD));
    assertTrue(codes.contains(Code.UNKNOWN_ENTITY_TYPE));
  }

  @Test
  void unknownFieldsNeverBecomeExecutable() {
    QueryIR ir =
        with(
            Schemas.fixture("commerce-filterless"),
            0,
            c -> constraint(c, "discount_code", c.operator(), c.value()));
    List<SchemaViolation> violations = SchemaValidator.validate(ir, Schemas.commerce("1.0"));
    assertEquals(
        List.of(Code.UNKNOWN_FIELD), violations.stream().map(SchemaViolation::code).toList());
    assertEquals("/constraints/0/field", violations.getFirst().path());
  }

  @Test
  void operatorsMustBeAllowedOnTheField() {
    QueryIR ir =
        with(
            Schemas.fixture("commerce-filterless"),
            2,
            c -> constraint(c, c.field(), Operator.LT, c.value()));
    assertEquals(List.of(Code.OPERATOR_NOT_ALLOWED), codes(ir, Schemas.commerce("1.0")));
  }

  @Test
  void valueTypesMustFitTheFieldType() {
    QueryIR ir =
        with(
            Schemas.fixture("commerce-filterless"),
            0,
            c ->
                constraint(
                    c,
                    c.field(),
                    c.operator(),
                    Optional.of(new IrValue.Number(new BigDecimal("1200")))));
    assertEquals(
        List.of(Code.TYPE_MISMATCH),
        codes(ir, Schemas.commerce("1.0")),
        "price needs money, not a bare number");
  }

  @Test
  void quantityUnitsMustMatch() {
    QueryIR ir =
        with(
            Schemas.fixture("devops-pods"),
            0,
            c ->
                constraint(
                    c,
                    c.field(),
                    c.operator(),
                    Optional.of(new IrValue.Quantity(new BigDecimal("85"), "MiB"))));
    assertEquals(List.of(Code.UNIT_MISMATCH), codes(ir, Schemas.devops()));
  }

  @Test
  void numericBoundsAreEnforced() {
    QueryIR ir =
        with(
            Schemas.fixture("devops-pods"),
            0,
            c ->
                constraint(
                    c,
                    c.field(),
                    c.operator(),
                    Optional.of(new IrValue.Quantity(new BigDecimal("185"), "%"))));
    assertEquals(List.of(Code.OUT_OF_RANGE), codes(ir, Schemas.devops()));
  }

  @Test
  void closedVocabulariesAreEnforcedInsideLists() {
    QueryIR ir =
        with(
            Schemas.fixture("security-ips"),
            1,
            c ->
                constraint(
                    c,
                    c.field(),
                    c.operator(),
                    Optional.of(
                        new IrValue.ListOf(
                            List.of(new IrValue.Text("trusted"), new IrValue.Text("vip"))))));
    List<SchemaViolation> violations = SchemaValidator.validate(ir, Schemas.security());
    assertEquals(
        List.of(Code.VALUE_NOT_ALLOWED), violations.stream().map(SchemaViolation::code).toList());
    assertEquals("/constraints/1/value/values/1", violations.getFirst().path());
  }

  @Test
  void rangeBoundsAreCheckedIndividually() {
    QueryIR ir = Schemas.fixture("multi-step");
    assertEquals(List.of(), codes(ir, Schemas.commerce("1.0")));
    QueryIR wrong =
        with(
            ir,
            3,
            c ->
                constraint(
                    c,
                    c.field(),
                    c.operator(),
                    Optional.of(
                        new IrValue.Range(
                            Optional.of(new IrValue.Number(BigDecimal.ONE)),
                            Optional.empty(),
                            true,
                            true))));
    assertEquals(List.of(Code.TYPE_MISMATCH), codes(wrong, Schemas.commerce("1.0")));
  }

  @Test
  void messagesDoNotEchoUserValues() {
    QueryIR ir =
        with(
            Schemas.fixture("security-ips"),
            1,
            c ->
                constraint(
                    c, c.field(), Operator.EQ, Optional.of(new IrValue.Text("secret-token-xyz"))));
    for (SchemaViolation violation : SchemaValidator.validate(ir, Schemas.security())) {
      assertFalse(violation.message().contains("secret-token-xyz"));
    }
  }

  // --- helpers ---------------------------------------------------------------------------

  private static List<Code> codes(QueryIR ir, DomainSchema schema) {
    return SchemaValidator.validate(ir, schema).stream().map(SchemaViolation::code).toList();
  }

  private static Constraint constraint(
      Constraint c, String field, Operator op, Optional<IrValue> value) {
    return new Constraint(
        c.id(),
        field,
        op,
        value,
        c.strength(),
        c.provenance(),
        c.reason(),
        c.span(),
        c.confidence(),
        c.scope());
  }

  private static QueryIR with(
      QueryIR ir, int index, java.util.function.UnaryOperator<Constraint> change) {
    List<Constraint> constraints = new ArrayList<>(ir.constraints());
    constraints.set(index, change.apply(constraints.get(index)));
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        ir.entities(),
        constraints,
        ir.semanticSignals(),
        ir.retrievalHints(),
        ir.subqueries(),
        ir.extensions());
  }
}
