package io.qwenbridge.multilingual;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.core.queryir.QueryIrJson;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-286 in the v1 pipeline, together with QB-291: the typo-corrected search form is expanded to
 * every configured target language; the original is kept, the QueryIR is 1.1 with both the
 * correction and the expansion, and its constraints stay grounded in the original.
 */
@QuarkusTest
@TestProfile(ExpansionV1PipelineTest.Expansion.class)
class ExpansionV1PipelineTest {

  public static class Expansion implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.typo.enabled", "true",
          "qwenbridge.typo.languages", "en,sv",
          "qwenbridge.typo.mode", "SAFE_AUTO_CORRECT",
          "qwenbridge.typo.dictionary-dir", "src/test/resources/typo",
          "qwenbridge.multilingual.enabled", "true",
          "qwenbridge.multilingual.target-languages", "sv,en,fa",
          "qwenbridge.multilingual.providers", "glossary",
          "qwenbridge.multilingual.glossary-dir", "src/test/resources/multilingual/glossary");
    }
  }

  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void stub() {
    reset(openSearchClient, searchAnalysisService);
    SearchAnalysis analysis =
        SearchAnalysis.builder()
            .language("en")
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(0.9)
            .intentReason("test")
            .rewrites(List.of("q"))
            .semanticValidated(true)
            .semanticScore(0.9)
            .semanticMeaning("test")
            .entities(List.of())
            .searchMode(SearchMode.KEYWORD)
            .backend(SearchBackend.OPENSEARCH)
            .keywordSearch(true)
            .vectorSearch(false)
            .hybridSearch(false)
            .facets(false)
            .rerank(false)
            .rewriteAgain(false)
            .answer(false)
            .decisionConfidence(0.9)
            .decisionReason("test")
            .modelThreatDecision("ALLOW")
            .build();
    when(searchAnalysisService.analyze(anyString())).thenReturn(analysis);
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(analysis);
    when(openSearchClient.search(anyString(), anyMap()))
        .thenReturn(
            Map.of("took", 0, "hits", Map.of("total", Map.of("value", 0), "hits", List.of())));
  }

  @Test
  void theCorrectedQueryIsExpandedAndTheQueryIrRecordsBoth() throws Exception {
    String original = "blak shoes under 100 EUR";
    var response =
        given()
            .contentType("application/json")
            .body(Map.of("query", original))
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    assertThat(response.getString("originalQuery")).isEqualTo(original);
    assertThat(response.getString("spelling.corrected")).isEqualTo("black shoes under 100 EUR");
    // The corrected text is what is expanded; the original stays untouched.
    assertThat(response.getString("expansion.original")).isEqualTo(original);
    assertThat(response.getString("expansion.sourceText")).isEqualTo("black shoes under 100 EUR");
    assertThat(response.getString("expansion.source.language")).isEqualTo("en");
    assertThat(response.getList("expansion.targets.language")).containsExactly("sv", "en", "fa");
    assertThat(response.getList("expansion.targets.status"))
        .containsExactly("SUCCESS", "UNCHANGED", "LOW_CONFIDENCE");
    assertThat(response.getString("expansion.targets[0].text"))
        .isEqualTo("svarta skor under 100 EUR");
    // The Persian table lacks "black" and "shoes": the low share of covered words is reported.
    assertThat(response.getString("expansion.targets[2].reason")).startsWith("coverage");

    String irJson =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(response.getMap("queryIR"));
    var ir = ((QueryIrJson.Result.Valid) QueryIrJson.read(irJson)).ir();
    assertThat(ir.version().toString()).isEqualTo("1.1");
    assertThat(ir.originalText()).isEqualTo(original);
    assertThat(ir.correction()).isPresent();
    var expansion = ir.expansion().orElseThrow();
    assertThat(expansion.status()).isEqualTo("PARTIAL");
    assertThat(expansion.configuredTargets()).containsExactly("sv", "en", "fa");
    assertThat(expansion.targets()).hasSize(3);
    assertThat(expansion.targets().get(0).retrieval()).isTrue();
    for (var c : ir.constraints()) c.span().ifPresent(s -> assertThat(original).contains(s.text()));
  }
}
