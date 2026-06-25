package io.qwenbridge.api;

import io.qwenbridge.QwenBridgeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = QwenBridgeApplication.class)
@AutoConfigureMockMvc
class SearchAnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAnalyzePersianQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"میز"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("میز"))
                .andExpect(jsonPath("$.language").value("fa"))
                .andExpect(jsonPath("$.intent").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.decision").value("REWRITE"))
                .andExpect(jsonPath("$.rewrites", hasSize(3)))
                .andExpect(jsonPath("$.threatReasons", hasSize(0)))
                .andExpect(jsonPath("$.semanticValidated").value(true))
                .andExpect(jsonPath("$.policyPassed").value(true))
                .andExpect(jsonPath("$.pipelineTrace", hasSize(8)))
                .andExpect(jsonPath("$.pipelineTrace[0].step").value("LanguageStep"))
                .andExpect(jsonPath("$.pipelineTrace[0].status").value("EXECUTED"));
    }

    @Test
    void shouldBlockSqlInjectionQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"desk union select password from users"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.threatReasons", hasItem("SQL_INJECTION")))
                .andExpect(jsonPath("$.rewrites", hasSize(0)))
                .andExpect(jsonPath("$.pipelineTrace", hasSize(8)))
                .andExpect(jsonPath("$.pipelineTrace[0].status").value("EXECUTED"))
                .andExpect(jsonPath("$.pipelineTrace[1].status").value("EXECUTED"))
                .andExpect(jsonPath("$.pipelineTrace[2].status").value("EXECUTED"))
                .andExpect(jsonPath("$.pipelineTrace[3].status").value("SKIPPED"));
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
