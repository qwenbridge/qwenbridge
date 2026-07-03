package io.qwenbridge.api;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchAnalyzeCacheDegradationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIAnalysisCache cache;

    @MockBean
    private AIService aiService;

    @MockBean
    private OpenSearchClient openSearchClient;

    @Test
    void shouldDegradeSafelyWhenRedisCacheFails() throws Exception {
        when(cache.get(any(CacheKey.class))).thenThrow(new RuntimeException("Redis unavailable"));
        doThrow(new RuntimeException("Redis unavailable"))
                .when(cache)
                .put(any(CacheKey.class), any());
        when(aiService.chat(any(ChatRequest.class)))
                .thenReturn(new ChatResponse(analysisJson()));
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
