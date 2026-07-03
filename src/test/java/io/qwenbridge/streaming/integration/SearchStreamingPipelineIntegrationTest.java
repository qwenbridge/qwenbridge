package io.qwenbridge.streaming.integration;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchStreamingPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StreamingSessionRegistry registry;

    @MockBean
    private AIService aiService;

    @MockBean
    private OpenSearchClient openSearchClient;

    @MockBean
    private SearchAnalysisService searchAnalysisService;

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void shouldCompleteOnlyStreamSessionsForMatchingPipelineRequest() throws Exception {
        String requestId = "stream-request-1";
        String unrelatedRequestId = "stream-request-2";

        openStream(requestId);
        openStream(unrelatedRequestId);

        assertThat(registry.findByRequestId(requestId)).hasSize(1);
        assertThat(registry.findByRequestId(unrelatedRequestId)).hasSize(1);

        stubSuccessfulPipeline();

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "query": "table"
                                }
                                """.formatted(requestId)))
                .andExpect(status().isOk());

        assertThat(registry.findByRequestId(requestId)).isEmpty();

        assertThat(registry.findByRequestId(unrelatedRequestId))
                .hasSize(1);
    }

    private void openStream(String requestId) throws Exception {
        mockMvc.perform(get("/api/v1/search/stream/{requestId}", requestId))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    private void stubSuccessfulPipeline() {
        when(aiService.chat(any(ChatRequest.class)))
                .thenReturn(new ChatResponse(analysisJson()));

        when(searchAnalysisService.analyze("table"))
                .thenReturn(searchAnalysis());

        when(openSearchClient.search(anyString(), anyMap()))
                .thenReturn(emptyOpenSearchResponse());
    }

    private SearchAnalysis searchAnalysis() {
        return SearchAnalysis.builder()
                .language("en")
                .intent(IntentType.PRODUCT_SEARCH)
                .intentConfidence(0.90)
                .intentReason("User is searching for a product.")
                .rewrites(List.of("table"))
                .semanticValidated(true)
                .semanticScore(0.85)
                .semanticMeaning("Product search query.")
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
                .decisionConfidence(0.85)
                .decisionReason("Use OpenSearch keyword search.")
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