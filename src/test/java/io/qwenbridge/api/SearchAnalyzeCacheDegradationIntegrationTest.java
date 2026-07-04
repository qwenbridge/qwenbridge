package io.qwenbridge.api;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import io.qwenbridge.testsupport.TestMockConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMockConfiguration.class)
class SearchAnalyzeCacheDegradationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AIAnalysisCache cache;

    @Autowired
    private AIService aiService;

    @Autowired
    private OpenSearchClient openSearchClient;

    @Autowired
    private SearchAnalysisService searchAnalysisService;

    @BeforeEach
    void resetMocks() {
        reset(cache, aiService, openSearchClient, searchAnalysisService);
    }

    @Test
    void shouldDegradeSafelyWhenRedisCacheFails() throws Exception {
        when(cache.get(any(CacheKey.class))).thenThrow(new RuntimeException("Redis unavailable"));
        doThrow(new RuntimeException("Redis unavailable"))
                .when(cache)
                .put(any(CacheKey.class), any());
        when(aiService.chat(any(ChatRequest.class)))
                .thenReturn(new ChatResponse(analysisJson()));
        when(searchAnalysisService.analyze("table"))
                .thenReturn(searchAnalysis());
        when(openSearchClient.search(anyString(), anyMap()))
                .thenReturn(emptyOpenSearchResponse());

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"table\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("table"))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.search.available").value(true));
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
                "took", 0,
                "hits", Map.of(
                        "total", Map.of("value", 0),
                        "hits", List.of()
                )
        );
    }
}
