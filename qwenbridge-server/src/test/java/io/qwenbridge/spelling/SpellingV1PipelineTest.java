package io.qwenbridge.spelling;

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
import org.mockito.ArgumentCaptor;

/**
 * QB-291 in the v1 pipeline: the corrected search form feeds the model analysis, the original is
 * kept, the QueryIR is 1.1 with the correction, and its constraints stay grounded in the original.
 */
@QuarkusTest
@TestProfile(SpellingV1PipelineTest.Typo.class)
class SpellingV1PipelineTest {

  public static class Typo implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.typo.enabled", "true",
          "qwenbridge.typo.languages", "en,sv",
          "qwenbridge.typo.mode", "SAFE_AUTO_CORRECT",
          "qwenbridge.typo.dictionary-dir", "src/test/resources/typo");
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
  void theCorrectedSearchFormFeedsAnalysisAndTheQueryIrRecordsIt() throws Exception {
    var response =
        given()
            .contentType("application/json")
            .body(Map.of("query", "blak shoes under 100 EUR"))
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    assertThat(response.getString("originalQuery")).isEqualTo("blak shoes under 100 EUR");
    assertThat(response.getString("spelling.status")).isEqualTo("CORRECTED");
    assertThat(response.getString("spelling.corrected")).isEqualTo("black shoes under 100 EUR");
    var captor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(searchAnalysisService, org.mockito.Mockito.atLeastOnce())
        .analyze(captor.capture(), anyString());
    assertThat(captor.getAllValues()).contains("black shoes under 100 EUR");
    String irJson =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(response.getMap("queryIR"));
    var ir = ((QueryIrJson.Result.Valid) QueryIrJson.read(irJson)).ir();
    assertThat(ir.version().toString()).isEqualTo("1.1");
    assertThat(ir.originalText()).isEqualTo("blak shoes under 100 EUR");
    assertThat(ir.correction().orElseThrow().correctedText())
        .isEqualTo("black shoes under 100 EUR");
    for (var c : ir.constraints())
      c.span().ifPresent(s -> assertThat("blak shoes under 100 EUR").contains(s.text()));
  }
}
