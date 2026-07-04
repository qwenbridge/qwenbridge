package io.qwenbridge.api;

import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.operations.health.DependencyHealth;
import io.qwenbridge.operations.health.OperationalHealthService;
import io.qwenbridge.operations.health.OperationalStatus;
import io.qwenbridge.operations.health.ReadinessHealthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import io.qwenbridge.testsupport.TestMockConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMockConfiguration.class)
class ApiHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AIService aiService;

    @Autowired
    private OpenSearchClient openSearchClient;

    @Autowired
    private SearchAnalysisService searchAnalysisService;

    @Autowired
    private OperationalHealthService operationalHealthService;

    @BeforeEach
    void resetMocks() {
        reset(aiService, openSearchClient, searchAnalysisService, operationalHealthService);
    }

    @Test
    void shouldReturnPublicHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("X-QwenBridge-Version", "0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("qwenbridge"))
                .andExpect(jsonPath("$.apiVersion").value("v1"));
    }

    @Test
    void shouldReturnLivenessStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldReturnReadinessStatusWhenDependenciesAreDegraded() throws Exception {
        when(operationalHealthService.readiness()).thenReturn(ReadinessHealthResponse.builder()
                .status(OperationalStatus.DEGRADED)
                .service("qwenbridge")
                .apiVersion("v1")
                .checkedAt(Instant.parse("2026-07-04T11:00:00Z"))
                .dependencies(List.of(DependencyHealth.degraded("ollama", "unavailable", 4)))
                .build());

        mockMvc.perform(get("/api/v1/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.dependencies[0].name").value("ollama"))
                .andExpect(jsonPath("$.dependencies[0].reason").value("unavailable"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenReadinessIsDown() throws Exception {
        when(operationalHealthService.readiness()).thenReturn(ReadinessHealthResponse.builder()
                .status(OperationalStatus.DOWN)
                .service("qwenbridge")
                .apiVersion("v1")
                .checkedAt(Instant.parse("2026-07-04T11:00:00Z"))
                .dependencies(List.of(DependencyHealth.down("redis", "unavailable", 2)))
                .build());

        mockMvc.perform(get("/api/v1/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.dependencies[0].name").value("redis"));
    }
}