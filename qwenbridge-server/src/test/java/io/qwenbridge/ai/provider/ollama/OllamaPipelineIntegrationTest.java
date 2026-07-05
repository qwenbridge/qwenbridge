package io.qwenbridge.ai.provider.ollama;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class OllamaPipelineIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldAnalyzeSearchQueryUsingRealOllamaRewrite() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/search/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"tabel\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalQuery").value("tabel"))
        .andExpect(jsonPath("$.decision").value("ALLOW"))
        .andExpect(jsonPath("$.rewrites[0]", not(blankOrNullString())))
        .andExpect(jsonPath("$.policyPassed").value(true));
  }
}
