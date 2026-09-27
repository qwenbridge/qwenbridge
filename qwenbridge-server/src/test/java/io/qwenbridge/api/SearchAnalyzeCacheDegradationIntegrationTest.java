package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SearchAnalyzeCacheDegradationIntegrationTest {

  @InjectMock AIAnalysisCache cache;

  @InjectMock AIService aiService;

  @InjectMock OpenSearchClient openSearchClient;

  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void resetMocks() {
    reset(cache, aiService, openSearchClient, searchAnalysisService);
  }

  @Test
  void shouldDegradeSafelyWhenRedisCacheFails() {
    when(cache.get(any(CacheKey.class))).thenThrow(new RuntimeException("Redis unavailable"));
    doThrow(new RuntimeException("Redis unavailable")).when(cache).put(any(CacheKey.class), any());
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(analysisJson()));
    when(searchAnalysisService.analyze("table")).thenReturn(searchAnalysis());
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(searchAnalysis());
    when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());

    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"table\"}")
        .when()
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("originalQuery", equalTo("table"))
        .body("decision", equalTo("ALLOW"))
        .body("search.available", equalTo(true));
  }

  private SearchAnalysis searchAnalysis() {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(0.85)
        .intentReason("Product search.")
        .rewrites(List.of("table"))
        .semanticValidated(true)
        .semanticScore(0.90)
        .semanticMeaning("Product search.")
        .entities(List.of("table"))
        .searchMode(SearchMode.KEYWORD)
        .backend(SearchBackend.OPENSEARCH)
        .keywordSearch(true)
        .vectorSearch(false)
        .hybridSearch(false)
        .facets(true)
        .rerank(false)
        .rewriteAgain(false)
        .answer(false)
        .decisionConfidence(0.80)
        .decisionReason("Keyword search is enough.")
        .build();
  }

  private String analysisJson() {
    return """
           {
             "language": "en",
             "intent": "PRODUCT_SEARCH",
             "intentConfidence": 0.85,
             "intentReason": "Product search.",
             "rewrites": ["table"],
             "semanticValidated": true,
             "semanticScore": 0.90,
             "semanticMeaning": "Product search.",
             "entities": ["table"],
             "searchMode": "KEYWORD",
             "backend": "OPENSEARCH",
             "keywordSearch": true,
             "vectorSearch": false,
             "hybridSearch": false,
             "facets": true,
             "rerank": false,
             "rewriteAgain": false,
             "answer": false,
             "decisionConfidence": 0.80,
             "decisionReason": "Keyword search is enough."
           }
           """;
  }

  private Map<String, Object> emptyOpenSearchResponse() {
    return Map.of(
        "took",
        0,
        "hits",
        Map.of(
            "total", Map.of("value", 0),
            "hits", List.of()));
  }
}
