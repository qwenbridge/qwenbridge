package io.qwenbridge.api;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchAnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIRewriteService aiRewriteService;

    @MockBean
    private OpenSearchClient openSearchClient;

    @MockBean
    private SearchAnalysisService searchAnalysisService;

    @Test
    void shouldAnalyzePersianQuery() throws Exception {
        when(aiRewriteService.rewrite("میز")).thenReturn("table");
        when(searchAnalysisService.analyze("میز")).thenReturn(searchAnalysis("fa", "table"));
        when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"میز\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("میز"))
                .andExpect(jsonPath("$.language").value("fa"))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.rewrites[0]").value("table"))
                .andExpect(jsonPath("$.policyPassed").value(true))
                .andExpect(jsonPath("$.search.available").value(true))
                .andExpect(jsonPath("$.search.hits").isArray());
    }

    @Test
    void shouldAnalyzeEnglishQuery() throws Exception {
        when(aiRewriteService.rewrite("table")).thenReturn("table");
        when(searchAnalysisService.analyze("table")).thenReturn(searchAnalysis("en", "table"));
        when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"table\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("table"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.rewrites[0]").value("table"))
                .andExpect(jsonPath("$.executionPlan.available").value(true))
                .andExpect(jsonPath("$.executionPlan.mode").exists())
                .andExpect(jsonPath("$.executionPlan.backend").exists())
                .andExpect(jsonPath("$.executionPlan.steps").isArray())
                .andExpect(jsonPath("$.executionPlan.steps[0].operation").exists())
                .andExpect(jsonPath("$.search.available").value(true))
                .andExpect(jsonPath("$.search.hits").isArray());
    }

    @Test
    void shouldReturnExecutionPlanAndExecutionResult() throws Exception {
        when(aiRewriteService.rewrite("table")).thenReturn("table");
        when(searchAnalysisService.analyze("table")).thenReturn(searchAnalysis("en", "table"));
        when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"table\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.executionPlan.available").value(true))
                .andExpect(jsonPath("$.executionResult.available").value(true))
                .andExpect(jsonPath("$.executionResult.executed").value(true))
                .andExpect(jsonPath("$.executionResult.operations").isArray())
                .andExpect(jsonPath("$.executionResult.results").isArray())
                .andExpect(jsonPath("$.search.available").value(true))
                .andExpect(jsonPath("$.search.totalHits").value(0))
                .andExpect(jsonPath("$.search.tookMillis").value(0))
                .andExpect(jsonPath("$.search.hits").isArray());
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private SearchAnalysis searchAnalysis(String language, String rewrite) {
        return new SearchAnalysis(
                language,
                IntentType.PRODUCT_SEARCH,
                0.90,
                "User is searching for a product.",
                List.of(rewrite),
                true,
                0.85,
                "Product search query.",
                List.of(rewrite),
                SearchMode.KEYWORD,
                SearchBackend.OPENSEARCH,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                0.85,
                "Use OpenSearch keyword search."
        );
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