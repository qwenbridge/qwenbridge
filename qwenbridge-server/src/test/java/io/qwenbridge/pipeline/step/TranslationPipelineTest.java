package io.qwenbridge.pipeline.step;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-016, QB-017, QB-020 with the default configuration: no translation provider is configured, the
 * server works, and a request for another content language gets an explicit unsupported result
 * while the query still asks for cross-language retrieval.
 */
@QuarkusTest
public class TranslationPipelineTest {

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  public static SearchAnalysis allowAnalysis() {
    return analysisWith("ALLOW");
  }

  /** A validated model analysis with the given model safety decision. */
  public static SearchAnalysis analysisWith(String modelThreatDecision) {
    return SearchAnalysis.builder()
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
        .modelThreatDecision(modelThreatDecision)
        .build();
  }

  public static void stub(
      AIService ai, OpenSearchClient openSearch, SearchAnalysisService analysisService) {
    reset(ai, openSearch, analysisService);
    SearchAnalysis analysis = allowAnalysis();
    when(ai.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("{}"));
    when(analysisService.analyze(anyString())).thenReturn(analysis);
    when(analysisService.analyze(anyString(), anyString())).thenReturn(analysis);
    when(openSearch.search(anyString(), anyMap()))
        .thenReturn(
            Map.of("took", 0, "hits", Map.of("total", Map.of("value", 0), "hits", List.of())));
  }

  public static JsonPath analyze(Map<String, Object> body) {
    String json =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    return JsonPath.from(json);
  }

  @BeforeEach
  void setUp() {
    stub(aiService, openSearchClient, searchAnalysisService);
  }

  @Test
  void withoutContentLanguageNothingIsTranslatedButTextFormsAreReported() {
    JsonPath r = analyze(Map.of("query", "Ｂlack  shoes under 100 EUR"));

    assertThat(r.getString("translation.status")).isEqualTo("SKIPPED");
    assertThat(r.getString("translation.reason")).isEqualTo("NOT_REQUESTED");
    assertThat(r.getString("textRepresentations.original")).isEqualTo("Ｂlack  shoes under 100 EUR");
    assertThat(r.getString("textRepresentations.normalized")).isNotNull();
    assertThat(r.getString("textRepresentations.translated")).isNull();
    assertThat(r.getString("textRepresentations.transformations[0].kind"))
        .isEqualTo("NORMALIZATION");
    assertThat(r.getString("textRepresentations.transformations[0].actor"))
        .isEqualTo("search-normalization-v1");
    assertThat(r.getBoolean("queryIR.retrievalHints.crossLanguage")).isFalse();
  }

  @Test
  void otherContentLanguageWithoutProviderIsExplicitlyUnsupported() {
    JsonPath r =
        analyze(
            Map.of(
                "query",
                "کفش مشکی بدون چرم زیر ۱۲۰۰ یورو",
                "context",
                Map.of("content_language", "en")));

    assertThat(r.getString("translation.status")).isEqualTo("UNSUPPORTED");
    assertThat(r.getString("translation.reason")).isEqualTo("NO_PROVIDER");
    assertThat(r.getString("translation.sourceLanguage")).isEqualTo("fa");
    assertThat(r.getString("translation.targetLanguage")).isEqualTo("en");
    assertThat(r.getString("textRepresentations.translated")).isNull();
    assertThat(r.getString("decision")).isNotEqualTo("BLOCK");
    assertThat(r.getBoolean("queryIR.retrievalHints.crossLanguage")).isTrue();
    assertThat(r.getList("queryIR.retrievalHints.contentLanguages", String.class))
        .containsExactly("en");
    verify(aiService, never())
        .chat(argThat(req -> req != null && req.prompt().startsWith("Translate")));
  }

  @Test
  void sameContentLanguageIsSkipped() {
    JsonPath r =
        analyze(
            Map.of(
                "query",
                "کفش مشکی بدون چرم زیر ۱۲۰۰ یورو",
                "context",
                Map.of("content_language", "fa-IR")));

    assertThat(r.getString("translation.status")).isEqualTo("SKIPPED");
    assertThat(r.getString("translation.reason")).isEqualTo("SAME_LANGUAGE");
    assertThat(r.getBoolean("queryIR.retrievalHints.crossLanguage")).isFalse();
  }
}
