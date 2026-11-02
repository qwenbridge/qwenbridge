package io.qwenbridge.core.queryir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.core.queryir.IrViolation.Code;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** QB-037, QB-041, QB-042: golden fixtures, strict parsing and schema-evolution rules. */
class QueryIrJsonTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  static Stream<String> goldenFixtures() {
    return Stream.of(
        "commerce-filterless",
        "devops-pods",
        "security-ips",
        "observability-latency",
        "persian-ambiguous-currency",
        "multi-step");
  }

  static String fixture(String name) {
    try (InputStream in =
        QueryIrJsonTest.class.getResourceAsStream("/queryir/v1.0/" + name + ".json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException("fixture " + name, e);
    }
  }

  static ObjectNode tree(String name) {
    try {
      return (ObjectNode) JSON.readTree(fixture(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static QueryIrJson.Result read(JsonNode node) {
    return QueryIrJson.read(node.toString());
  }

  static List<Code> codes(QueryIrJson.Result result) {
    return assertInstanceOf(QueryIrJson.Result.Invalid.class, result).violations().stream()
        .map(IrViolation::code)
        .toList();
  }

  static List<Code> mutate(String fixture, Consumer<ObjectNode> mutation) {
    ObjectNode node = tree(fixture);
    mutation.accept(node);
    return codes(read(node));
  }

  // --- Golden fixtures -----------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFixtures")
  void goldenFixtureIsValidAndRoundTripsLosslessly(String name) throws IOException {
    QueryIrJson.Result result = QueryIrJson.read(fixture(name));
    QueryIR ir =
        assertInstanceOf(QueryIrJson.Result.Valid.class, result, () -> result.toString()).ir();

    String written = QueryIrJson.write(ir);
    assertEquals(
        JSON.readTree(fixture(name)), JSON.readTree(written), "round trip changed " + name);
    assertEquals(
        written,
        QueryIrJson.write(
            read(JSON.readTree(written)) instanceof QueryIrJson.Result.Valid v ? v.ir() : null),
        "serialization must be deterministic");
  }

  @Test
  void unrelatedDomainsShareOneContract() {
    QueryIR commerce = valid("commerce-filterless");
    QueryIR devops = valid("devops-pods");
    QueryIR security = valid("security-ips");
    QueryIR observability = valid("observability-latency");

    assertEquals(3, commerce.hardConstraints().size());
    assertEquals("material", commerce.negativeConstraints().getFirst().field());
    assertEquals("kubernetes.pod", devops.entities().getFirst().type());
    assertEquals(Constraint.Operator.NOT_IN, security.negativeConstraints().getFirst().operator());
    assertEquals("latency.p99", observability.semanticSignals().getFirst().target().orElseThrow());
    assertTrue(commerce.retrievalHints().hybrid());
    assertFalse(devops.retrievalHints().hybrid());
  }

  @Test
  void ambiguousCurrencyStaysUnresolvedAndPersianTextIsExact() {
    QueryIR ir = valid("persian-ambiguous-currency");
    IrValue.Money money =
        assertInstanceOf(IrValue.Money.class, ir.constraints().getFirst().value().orElseThrow());
    assertEquals(new BigDecimal("1200"), money.amount());
    assertTrue(money.currency().isEmpty(), "krona must not be guessed");
    assertTrue(ir.originalText().contains("‌"), "ZWNJ preserved");
    assertTrue(ir.retrievalHints().crossLanguage());
  }

  @Test
  void unresolvedRelativeWindowIsRepresentedNotGuessed() {
    IrValue.Temporal window =
        (IrValue.Temporal)
            valid("observability-latency").constraints().getFirst().value().orElseThrow();
    assertFalse(window.resolved());
    IrValue.Temporal resolved =
        (IrValue.Temporal) valid("devops-pods").constraints().get(1).value().orElseThrow();
    assertTrue(resolved.resolved());
  }

  @Test
  void decompositionKeepsParentConstraintsAndScopes() {
    QueryIR ir = valid("multi-step");
    assertEquals(2, ir.subqueries().size());
    assertEquals(List.of("c2", "c3"), ir.subqueries().get(1).constraintIds());
    assertEquals("s2", ir.constraints().get(2).scope().orElseThrow());
    assertTrue(ir.constraints().get(3).scope().isEmpty(), "budget applies to the whole request");
  }

  @Test
  void decimalAmountsKeepTheirExactValueAndScale() {
    ObjectNode node = tree("commerce-filterless");
    ObjectNode money = (ObjectNode) node.get("constraints").get(0).get("value");
    money.put("amount", new BigDecimal("12345678901234567890.123456789"));
    QueryIR ir = assertInstanceOf(QueryIrJson.Result.Valid.class, read(node)).ir();
    IrValue.Money parsed = (IrValue.Money) ir.constraints().getFirst().value().orElseThrow();
    assertEquals(new BigDecimal("12345678901234567890.123456789"), parsed.amount());
    assertTrue(QueryIrJson.write(ir).contains("12345678901234567890.123456789"));
  }

  // --- Version and evolution rules -------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"1.2", "2.0", "0.9", "1.10"})
  void unsupportedVersionsAreRejectedBeforeInterpretation(String version) {
    assertEquals(
        List.of(Code.UNSUPPORTED_VERSION),
        mutate(
            "commerce-filterless",
            n -> {
              n.put("irVersion", version);
              n.put("fieldFromTheFuture", true); // must not even be looked at
            }));
  }

  @ParameterizedTest
  @ValueSource(strings = {"v1", "1", "1.0.0", "01.0", ""})
  void malformedVersionsAreRejected(String version) {
    assertEquals(
        List.of(Code.INVALID_FORMAT), mutate("devops-pods", n -> n.put("irVersion", version)));
  }

  @Test
  void missingVersionIsRequired() {
    assertEquals(List.of(Code.REQUIRED), mutate("devops-pods", n -> n.remove("irVersion")));
  }

  @Test
  void unknownFieldsAreRejectedAtEveryLevel() {
    assertEquals(List.of(Code.UNKNOWN_FIELD), mutate("devops-pods", n -> n.put("priority", 1)));
    assertEquals(
        List.of(Code.UNKNOWN_FIELD),
        mutate("devops-pods", n -> ((ObjectNode) n.get("constraints").get(0)).put("weight", 2)));
    assertEquals(
        List.of(Code.UNKNOWN_FIELD),
        mutate(
            "devops-pods",
            n -> ((ObjectNode) n.get("constraints").get(0).get("value")).put("precision", 2)));
  }

  @Test
  void extensionsMayCarryAnyJsonLikeData() {
    ObjectNode node = tree("commerce-filterless");
    ((ObjectNode) node.get("extensions").get("commerce").get("data"))
        .putObject("futureDomainField")
        .putArray("nested")
        .add(1)
        .add("two");
    assertInstanceOf(QueryIrJson.Result.Valid.class, read(node));
  }

  @Test
  void minimalDocumentUsesDocumentedDefaults() {
    QueryIrJson.Result result =
        QueryIrJson.read("{\"irVersion\":\"1.0\",\"originalText\":\"pods\",\"language\":\"und\"}");
    QueryIR ir = assertInstanceOf(QueryIrJson.Result.Valid.class, result).ir();
    assertTrue(ir.intents().isEmpty());
    assertEquals(RetrievalHints.NONE, ir.retrievalHints());
    assertTrue(ir.language().isUndetermined());
    assertEquals(
        "{\"irVersion\":\"1.0\",\"originalText\":\"pods\",\"language\":\"und\"}",
        QueryIrJson.write(ir));
  }

  // --- Semantic validation -----------------------------------------------------------

  @Test
  void spansMustMatchTheOriginalText() {
    assertEquals(
        List.of(Code.INVALID_SPAN),
        mutate(
            "devops-pods",
            n -> {
              ObjectNode span = (ObjectNode) n.get("entities").get(0).get("span");
              span.put("start", 0).put("end", 15); // same length, wrong place
            }));
  }

  @Test
  void identifiersMustBeUniqueAndReferencesMustResolve() {
    assertTrue(
        mutate("devops-pods", n -> ((ObjectNode) n.get("constraints").get(1)).put("id", "c1"))
            .contains(Code.DUPLICATE_ID));
    assertTrue(
        mutate("multi-step", n -> ((ObjectNode) n.get("constraints").get(0)).put("scope", "s9"))
            .contains(Code.UNKNOWN_REFERENCE));
    assertTrue(
        mutate(
                "multi-step",
                n -> ((ArrayNode) n.get("subqueries").get(0).withArray("constraintIds")).add("c99"))
            .contains(Code.UNKNOWN_REFERENCE));
  }

  @Test
  void subqueryDependenciesMustBeAcyclic() {
    List<Code> codes =
        mutate(
            "multi-step",
            n -> {
              ((ObjectNode) n.get("subqueries").get(0)).putArray("dependsOn").add("s2");
              ((ObjectNode) n.get("subqueries").get(1)).putArray("dependsOn").add("s1");
            });
    assertEquals(List.of(Code.CYCLE), codes);
    assertEquals(
        List.of(Code.CYCLE),
        mutate(
            "multi-step",
            n -> ((ObjectNode) n.get("subqueries").get(0)).putArray("dependsOn").add("s1")));
  }

  @Test
  void operatorsMustFitTheirValues() {
    assertEquals(
        List.of(Code.OPERATOR_VALUE_MISMATCH),
        mutate(
            "devops-pods",
            n ->
                ((ObjectNode) n.get("constraints").get(0))
                    .set(
                        "value",
                        JSON.createObjectNode().put("type", "text").put("value", "high"))));
    assertEquals(
        List.of(Code.OPERATOR_VALUE_MISMATCH),
        mutate(
            "security-ips",
            n -> ((ObjectNode) n.get("constraints").get(0)).put("operator", "BETWEEN")));
    assertEquals(
        List.of(Code.OPERATOR_VALUE_MISMATCH),
        mutate(
            "security-ips",
            n -> ((ObjectNode) n.get("constraints").get(0)).put("operator", "NOT_EXISTS")));
    assertEquals(
        List.of(Code.REQUIRED),
        mutate("security-ips", n -> ((ObjectNode) n.get("constraints").get(0)).remove("value")));
  }

  @Test
  void rangesMustBeOrderedAndHomogeneous() {
    assertEquals(
        List.of(Code.INVALID_VALUE),
        mutate(
            "multi-step",
            n ->
                ((ObjectNode) n.get("constraints").get(3).get("value").get("min"))
                    .put("amount", 5000)));
    assertEquals(
        List.of(Code.INVALID_VALUE),
        mutate(
            "multi-step",
            n ->
                ((ObjectNode) n.get("constraints").get(3).get("value"))
                    .set("max", JSON.createObjectNode().put("type", "number").put("value", 3))));
  }

  @Test
  void provenanceRulesProtectHardConstraints() {
    assertEquals(
        List.of(Code.REQUIRED),
        mutate("devops-pods", n -> ((ObjectNode) n.get("constraints").get(0)).remove("span")));
    assertEquals(
        List.of(Code.INVALID_VALUE),
        mutate(
            "devops-pods",
            n -> ((ObjectNode) n.get("constraints").get(0)).put("provenance", "INFERRED")));
    assertEquals(
        List.of(Code.REQUIRED),
        mutate("devops-pods", n -> ((ObjectNode) n.get("constraints").get(0)).put("reason", " ")));
  }

  @Test
  void typedValuesAreValidated() {
    assertTrue(
        mutate(
                "commerce-filterless",
                n ->
                    ((ObjectNode) n.get("constraints").get(0).get("value"))
                        .put("currency", "kronor"))
            .contains(Code.INVALID_VALUE));
    assertEquals(
        List.of(Code.INVALID_VALUE),
        mutate(
            "devops-pods",
            n -> ((ObjectNode) n.get("constraints").get(1).get("value")).put("value", "one hour")));
    assertTrue(
        mutate(
                "security-ips",
                n -> ((ObjectNode) n.get("intents").get(0).get("confidence")).put("value", 1.5))
            .contains(Code.INVALID_VALUE));
    assertTrue(
        mutate(
                "devops-pods",
                n -> ((ObjectNode) n.get("intents").get(0).get("confidence")).put("value", 0.5))
            .contains(Code.INVALID_VALUE),
        "HEURISTIC confidence must not carry a number");
  }

  @Test
  void malformedAndOverlyNestedJsonAreTypedErrors() {
    assertEquals(List.of(Code.MALFORMED_JSON), codes(QueryIrJson.read("{\"irVersion\":")));
    assertEquals(List.of(Code.MALFORMED_JSON), codes(QueryIrJson.read("[1,2]")));
    String deep = "[".repeat(40) + "]".repeat(40);
    assertEquals(
        List.of(Code.MALFORMED_JSON),
        codes(QueryIrJson.read("{\"irVersion\":\"1.0\",\"x\":" + deep + "}")));
  }

  @Test
  void violationMessagesNeverEchoUserText() {
    String secret = "my-secret-token-12345";
    QueryIrJson.Result result =
        mutate2(
            "devops-pods",
            n -> {
              n.put("originalText", "pods " + secret);
              ((ObjectNode) n.get("constraints").get(0)).put("field", secret);
            });
    for (IrViolation violation :
        assertInstanceOf(QueryIrJson.Result.Invalid.class, result).violations()) {
      assertFalse(violation.message().contains(secret), violation.toString());
      assertFalse(violation.path().contains(secret), violation.toString());
    }
  }

  private static QueryIrJson.Result mutate2(String fixture, Consumer<ObjectNode> mutation) {
    ObjectNode node = tree(fixture);
    mutation.accept(node);
    return read(node);
  }

  private static QueryIR valid(String name) {
    return assertInstanceOf(QueryIrJson.Result.Valid.class, QueryIrJson.read(fixture(name))).ir();
  }
}
