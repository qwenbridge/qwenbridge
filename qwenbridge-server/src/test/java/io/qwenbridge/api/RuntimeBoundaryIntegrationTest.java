package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RuntimeBoundaryIntegrationTest.EnabledAbuse.class)
class RuntimeBoundaryIntegrationTest {
  public static class EnabledAbuse implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.abuse.enabled", "true", "qwenbridge.abuse-protection.redis-enabled", "false");
    }
  }

  @InjectMock AIService aiService;
  @Inject StreamingSessionRegistry registry;
  @TestHTTPResource URI baseUri;

  @AfterEach
  void cleanup() {
    registry.clear();
  }

  @Test
  void oversizedRequestsAreRejectedBeforeProviderInvocation() {
    when(aiService.chat(any())).thenReturn(new ChatResponse("ok"));
    given()
        .contentType("application/json")
        .body("{\"prompt\":\"" + "a".repeat(70000) + "\"}")
        .post("/api/v1/ai/chat")
        .then()
        .statusCode(413);
    verifyNoInteractions(aiService);
  }

  @Test
  void chunkedBodiesAreAlsoBoundedBeforeProviderInvocation() throws Exception {
    byte[] payload =
        ("{\"prompt\":\"" + "a".repeat(70000) + "\"}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
      var request =
          HttpRequest.newBuilder(baseUri.resolve("api/v1/ai/chat"))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofInputStream(
                      () -> new java.io.ByteArrayInputStream(payload)))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(413);
      assertThat(response.body()).contains("VALIDATION_ERROR");
      verifyNoInteractions(aiService);
    }
  }

  @Test
  void abuseHeadersAreActuallyAppliedToApiRequests() {
    given()
        .get("/api/v1/health")
        .then()
        .statusCode(200)
        .header("X-RateLimit-Limit", org.hamcrest.Matchers.notNullValue());
  }

  @Test
  void sseHeadersArePresentWhenTheFirstEventCommitsTheResponse() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
      var request =
          HttpRequest.newBuilder(baseUri.resolve("api/v1/search/stream/header-regression"))
              .timeout(Duration.ofSeconds(5))
              .header("X-Request-ID", "header-regression")
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Request-ID")).contains("header-regression");
        assertThat(response.headers().firstValue("X-QwenBridge-Version")).isPresent();
      }
    }
  }
}
