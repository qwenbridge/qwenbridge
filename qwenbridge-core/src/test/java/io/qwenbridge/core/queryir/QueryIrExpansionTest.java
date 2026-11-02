package io.qwenbridge.core.queryir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** QueryIR 1.1 multilingual expansion metadata (QB-286). */
class QueryIrExpansionTest {

  static QueryExpansion expansion() {
    return new QueryExpansion(
        "PARTIAL",
        "ALL_TARGETS",
        "red jacket under 100 EUR",
        Optional.of(new LanguageTag("en")),
        List.of("sv", "ja", "DK"),
        List.of(
            new QueryExpansion.Target(
                "sv",
                Optional.of(new LanguageTag("sv")),
                "SUCCESS",
                Optional.of("röd jacka under 100 EUR"),
                Optional.of("glossary"),
                "HEURISTIC",
                Optional.empty(),
                Optional.of("PRESERVED"),
                true),
            new QueryExpansion.Target(
                "ja",
                Optional.of(new LanguageTag("ja")),
                "UNSUPPORTED_LANGUAGE",
                Optional.empty(),
                Optional.empty(),
                "UNKNOWN",
                Optional.of("no installed provider produces ja"),
                Optional.empty(),
                false),
            new QueryExpansion.Target(
                "DK",
                Optional.empty(),
                "INVALID_LANGUAGE",
                Optional.empty(),
                Optional.empty(),
                "UNKNOWN",
                Optional.of("INVALID: DK is a region code"),
                Optional.empty(),
                false)));
  }

  @Test
  void roundTripsAndKeepsTheOriginalText() {
    var ir =
        QueryIR.of("red jacket under 100 EUR", new LanguageTag("en")).withExpansion(expansion());
    assertEquals(IrVersion.V1_1, ir.version());
    String json = QueryIrJson.write(ir);
    assertTrue(json.contains("\"expansion\""), json);
    var back = assertInstanceOf(QueryIrJson.Result.Valid.class, QueryIrJson.read(json)).ir();
    assertEquals(ir, back);
    assertEquals("red jacket under 100 EUR", back.originalText());
  }

  @Test
  void aVersion10DocumentCannotCarryIt() {
    String json =
        "{\"irVersion\":\"1.0\",\"originalText\":\"x\",\"language\":\"en\",\"expansion\":{}}";
    assertInstanceOf(QueryIrJson.Result.Invalid.class, QueryIrJson.read(json));
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
                Optional.empty(),
                Optional.of(expansion())));
  }

  @Test
  void correctionAndExpansionCoexist() {
    var ir = QueryIR.of("x", new LanguageTag("en")).withExpansion(expansion());
    var corrected =
        ir.withCorrection(
            new TextCorrection(
                "NO_CHANGES",
                "SUGGEST_ONLY",
                "x",
                "x",
                List.of("en"),
                List.of("en"),
                Optional.empty(),
                List.of()));
    assertTrue(corrected.expansion().isPresent(), "withCorrection keeps the expansion");
    var back =
        assertInstanceOf(
                QueryIrJson.Result.Valid.class, QueryIrJson.read(QueryIrJson.write(corrected)))
            .ir();
    assertEquals(corrected, back);
  }
}
