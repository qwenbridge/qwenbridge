package io.qwenbridge.api;

import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void shouldAnalyzePersianQuery() throws Exception {
        when(aiRewriteService.rewrite("میز")).thenReturn("table");

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"میز\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("میز"))
                .andExpect(jsonPath("$.language").value("fa"))
                .andExpect(jsonPath("$.intent").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.rewrites[0]").value("table"))
                .andExpect(jsonPath("$.policyPassed").value(true));
    }

    @Test
    void shouldAnalyzeEnglishQuery() throws Exception {
        when(aiRewriteService.rewrite("table")).thenReturn("table");

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
                .andExpect(jsonPath("$.executionPlan.steps[0].operation").exists());
    }

    @Test
    void shouldReturnExecutionPlanAndExecutionResult() throws Exception {
        when(aiRewriteService.rewrite("table")).thenReturn("table");

        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"table\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.executionPlan.available").value(true))
                .andExpect(jsonPath("$.executionPlan.mode").exists())
                .andExpect(jsonPath("$.executionPlan.backend").exists())
                .andExpect(jsonPath("$.executionPlan.steps").isArray())
                .andExpect(jsonPath("$.executionPlan.steps[0].operation").exists())
                .andExpect(jsonPath("$.executionResult.available").value(true))
                .andExpect(jsonPath("$.executionResult.executed").value(true))
                .andExpect(jsonPath("$.executionResult.operations").isArray())
                .andExpect(jsonPath("$.executionResult.results").isArray())
                .andExpect(jsonPath("$.executionResult.reason").exists())
                .andExpect(jsonPath("$.search.available").value(true))
                .andExpect(jsonPath("$.search.totalHits").exists())
                .andExpect(jsonPath("$.search.tookMillis").exists())
                .andExpect(jsonPath("$.search.hits").isArray())
                .andExpect(jsonPath("$.search.available").value(true))
                .andExpect(jsonPath("$.search.totalHits").exists())
                .andExpect(jsonPath("$.search.tookMillis").exists())
                .andExpect(jsonPath("$.search.hits").isArray());
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
