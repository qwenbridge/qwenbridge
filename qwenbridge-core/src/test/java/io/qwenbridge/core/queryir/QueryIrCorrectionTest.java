package io.qwenbridge.core.queryir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.core.request.LanguageTag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** QueryIR 1.1 (QB-291): the optional correction field; 1.0 documents are unchanged. */
class QueryIrCorrectionTest {

  static final TextCorrection CORRECTION =
      new TextCorrection(
          "CORRECTED",
          "SAFE_AUTO_CORRECT",
          "röd jakca",
          "röd jacka",
          List.of("sv"),
          List.of("sv"),
          Optional.of(new LanguageTag("sv")),
          List.of(
              new TextCorrection.Correction(
                  "jakca",
                  4,
                  9,
                  "sv",
                  "jacka",
                  new BigDecimal("0.912"),
                  true,
                  "sv: edit distance 0.8")));

  @Test
  void aCorrectionRoundTripsAndRaisesTheVersionToOnePointOne() throws Exception {
    QueryIR plain = QueryIR.of("röd jakca", new LanguageTag("sv"));
    assertEquals("1.0", plain.version().toString());
    assertTrue(QueryIrJson.write(plain).contains("\"irVersion\":\"1.0\""));
    assertFalse(QueryIrJson.write(plain).contains("correction"));
    QueryIR corrected = plain.withCorrection(CORRECTION);
    assertEquals("1.1", corrected.version().toString());
    assertEquals("röd jakca", corrected.originalText());
    String json = QueryIrJson.write(corrected);
    var read = QueryIrJson.read(json);
    var back = assertInstanceOf(QueryIrJson.Result.Valid.class, read, () -> read + " " + json).ir();
    assertEquals(corrected, back);
    assertEquals(
        new BigDecimal("0.912"),
        back.correction().orElseThrow().corrections().getFirst().confidence());
  }

  @Test
  void correctionIsAnUnknownFieldInAOnePointZeroDocument() throws Exception {
    ObjectNode node =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    QueryIrJson.write(
                        QueryIR.of("x y", new LanguageTag("en"))
                            .withCorrection(
                                new TextCorrection(
                                    "NO_CHANGES",
                                    "SAFE_AUTO_CORRECT",
                                    "x y",
                                    "x y",
                                    List.of("en"),
                                    List.of("en"),
                                    Optional.empty(),
                                    List.of()))));
    node.put("irVersion", "1.0");
    var invalid = assertInstanceOf(QueryIrJson.Result.Invalid.class, QueryIrJson.read(node));
    assertEquals(IrViolation.Code.UNKNOWN_FIELD, invalid.violations().getFirst().code());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QueryIR(
                IrVersion.CURRENT,
                Optional.empty(),
                "x",
                new LanguageTag("en"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RetrievalHints.NONE,
                List.of(),
                java.util.Map.of(),
                Optional.of(CORRECTION)));
  }

  @Test
  void malformedCorrectionsAreRejected() throws Exception {
    ObjectNode node =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    QueryIrJson.write(
                        QueryIR.of("röd jakca", new LanguageTag("sv")).withCorrection(CORRECTION)));
    ((ObjectNode) node.get("correction").get("corrections").get(0)).put("end", 99);
    assertInstanceOf(QueryIrJson.Result.Invalid.class, QueryIrJson.read(node));
    node =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    QueryIrJson.write(
                        QueryIR.of("röd jakca", new LanguageTag("sv")).withCorrection(CORRECTION)));
    ((ObjectNode) node.get("correction")).put("surprise", true);
    var invalid = assertInstanceOf(QueryIrJson.Result.Invalid.class, QueryIrJson.read(node));
    assertEquals(IrViolation.Code.UNKNOWN_FIELD, invalid.violations().getFirst().code());
  }
}
