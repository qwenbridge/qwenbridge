package io.qwenbridge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.testsupport.TestMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMockConfiguration.class)
class ApiVersionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AIService aiService;

  @Autowired private OpenSearchClient openSearchClient;

  @Autowired private SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void resetMocks() {
    reset(aiService, openSearchClient, searchAnalysisService);
  }

  @Test
  void shouldReturnVersionInformation() throws Exception {
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("ok"));

    mockMvc
        .perform(get("/api/v1/version"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-ID"))
        .andExpect(header().string("X-QwenBridge-Version", "0.1.0-SNAPSHOT"))
        .andExpect(jsonPath("$.name").value("qwenbridge"))
        .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
        .andExpect(jsonPath("$.apiVersion").value("v1"))
        .andExpect(jsonPath("$.javaVersion").exists());
  }
}
