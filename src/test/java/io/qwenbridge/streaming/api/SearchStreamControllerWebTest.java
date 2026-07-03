package io.qwenbridge.streaming.api;

import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchStreamControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StreamingSessionRegistry registry;

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void shouldExposeStableConnectedEventEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/search/stream/{requestId}", "request-1"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));

        assertThat(registry.findByRequestId("request-1")).hasSize(1);
    }

    @Test
    void shouldReturnApiErrorForUnsupportedStreamRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/search/stream/request@1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("requestId contains unsupported characters"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/search/stream/request@1"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().exists("X-QwenBridge-Version"));

        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldReturnApiErrorForRequestIdLongerThanMaximumLength() throws Exception {
        String requestId = "a".repeat(129);

        mockMvc.perform(get("/api/v1/search/stream/{requestId}", requestId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("requestId must not exceed 128 characters"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(registry.size()).isZero();
    }
}
