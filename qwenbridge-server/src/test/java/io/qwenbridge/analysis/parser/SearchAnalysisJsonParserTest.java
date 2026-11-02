package io.qwenbridge.analysis.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SearchAnalysisJsonParserTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final SearchAnalysisJsonParser parser = new SearchAnalysisJsonParser(mapper);
  private static final String QUERY = "headphones not refurbished under 100 EUR";
  private static final String COMPACT =
      """
      {"v":1,"l":"en","i":"PRODUCT_SEARCH","q":["headphones not refurbished under 100 EUR"],
       "e":["headphones","100 EUR"],"c":[0.9,0.8,0.7],"t":true,"s":"KEYWORD","o":["F","R"],"r":"Product search with exclusions."}
      """;

  @Test
  void compactOutputPreservesConstraintsAndExpandsTheExistingContract() {
    SearchAnalysis result = parser.parse(COMPACT, QUERY);
    assertThat(result.intent()).isEqualTo(IntentType.PRODUCT_SEARCH);
    assertThat(result.rewrites()).containsExactly(QUERY);
    assertThat(result.semanticMeaning()).isEqualTo(QUERY);
    assertThat(result.entities()).containsExactly("headphones", "100 EUR");
    assertThat(result.intentConfidence()).isEqualTo(.9);
    assertThat(result.semanticScore()).isEqualTo(.8);
    assertThat(result.decisionConfidence()).isEqualTo(.7);
    assertThat(result.keywordSearch()).isTrue();
    assertThat(result.vectorSearch()).isFalse();
    assertThat(result.facets()).isTrue();
    assertThat(result.rerank()).isTrue();
    assertThat(result.backend()).isEqualTo(SearchBackend.OPENSEARCH);
  }

  @Test
  void vectorAndHybridModesRetainTheirExecutionSemantics() {
    var vector = parser.parse(COMPACT.replace("\"KEYWORD\"", "\"VECTOR\""), QUERY);
    assertThat(vector.vectorSearch()).isTrue();
    assertThat(vector.keywordSearch()).isFalse();
    var hybrid = parser.parse(COMPACT.replace("\"KEYWORD\"", "\"HYBRID\""), QUERY);
    assertThat(hybrid.hybridSearch()).isTrue();
    var answer = parser.parse(COMPACT.replace("\"KEYWORD\"", "\"DIRECT_ANSWER\""), QUERY);
    assertThat(answer.answer()).isTrue();
    assertThat(answer.backend()).isEqualTo(SearchBackend.NONE);
    assertThat(answer.searchMode()).isEqualTo(SearchMode.DIRECT_ANSWER);
  }

  @Test
  void legacyResponsesStillWork() {
    var result =
        parser.parse(
            """
            {"language":"fa","intent":"COMPARE","intentConfidence":0.8,"rewrites":["compare Sony and Bose"],
             "semanticValidated":true,"semanticScore":0.9,"semanticMeaning":"Compare two brands", "entities":["Sony","Bose"],
             "searchMode":"HYBRID","backend":"OPENSEARCH","hybridSearch":true,"decisionConfidence":0.7}
            """,
            "سونی و بوز را مقایسه کن");
    assertThat(result.intent()).isEqualTo(IntentType.COMPARE);
    assertThat(result.semanticMeaning()).isEqualTo("Compare two brands");
    assertThat(result.hybridSearch()).isTrue();
  }

  @TestFactory
  Stream<DynamicTest> malformedCompactOutputFailsClosed() {
    return Stream.of(
        rejects("unsupported version", n -> n.put("v", 2)),
        rejects("wrapped version", n -> n.put("v", 4294967297L)),
        rejects("unknown intent", n -> n.put("i", "EXECUTE_SQL")),
        rejects("unknown mode", n -> n.put("s", "CUSTOM_SQL")),
        rejects("wrong confidence count", n -> n.putArray("c").add(.9)),
        rejects("string confidence", n -> n.putArray("c").add("0.9").add(.9).add(.9)),
        rejects("nonfinite confidence", n -> n.putArray("c").add("NaN").add(.9).add(.9)),
        rejects("unbounded confidence", n -> n.putArray("c").add(1.1).add(.9).add(.9)),
        rejects("untyped validation", n -> n.put("t", "true")),
        rejects("missing rewrite", n -> n.putArray("q")),
        rejects("blank rewrite", n -> n.putArray("q").add(" ")),
        rejects("unknown operation", n -> n.putArray("o").add("X")),
        rejects("duplicate operation", n -> n.putArray("o").add("F").add("F")),
        rejects("missing field", n -> n.remove("i")),
        rejects("unexpected field", n -> n.put("backend", "CUSTOM")));
  }

  private DynamicTest rejects(String name, Consumer<ObjectNode> mutate) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          ObjectNode root = (ObjectNode) mapper.readTree(COMPACT);
          mutate.accept(root);
          assertThat(parser.parse(mapper.writeValueAsString(root), QUERY))
              .isEqualTo(SearchAnalysis.fallback(QUERY));
        });
  }
}
