package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.operations.health.DependencyHealth;
import io.qwenbridge.operations.health.OperationalHealthService;
import io.qwenbridge.operations.health.OperationalStatus;
import io.qwenbridge.operations.health.ReadinessHealthResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApiHealthControllerTest {

  @InjectMock AIService aiService;

  @InjectMock OpenSearchClient openSearchClient;

  @InjectMock SearchAnalysisService searchAnalysisService;

  @InjectMock OperationalHealthService operationalHealthService;

  @BeforeEach
  void resetMocks() {
    reset(aiService, openSearchClient, searchAnalysisService, operationalHealthService);
  }

  @Test
  void shouldReturnPublicHealthStatus() {
    given()
        .when()
        .get("/api/v1/health")
        .then()
        .statusCode(200)
        .header("X-Request-ID", notNullValue())
        .header("X-QwenBridge-Version", "0.1.0-SNAPSHOT")
        .body("status", equalTo("UP"))
        .body("service", equalTo("qwenbridge"))
        .body("apiVersion", equalTo("v1"));
  }

  @Test
  void shouldReturnLivenessStatus() {
    given().when().get("/api/v1/health/live").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  void shouldReturnReadinessStatusWhenDependenciesAreDegraded() {
    when(operationalHealthService.readiness())
        .thenReturn(
            ReadinessHealthResponse.builder()
                .status(OperationalStatus.DEGRADED)
                .service("qwenbridge")
                .apiVersion("v1")
                .checkedAt(Instant.parse("2026-07-04T11:00:00Z"))
                .dependencies(List.of(DependencyHealth.degraded("ollama", "unavailable", 4)))
                .build());

    given()
        .when()
        .get("/api/v1/health/ready")
        .then()
        .statusCode(200)
        .body("status", equalTo("DEGRADED"))
        .body("dependencies[0].name", equalTo("ollama"))
        .body("dependencies[0].reason", equalTo("unavailable"));
  }

  @Test
  void shouldReturnServiceUnavailableWhenReadinessIsDown() {
    when(operationalHealthService.readiness())
        .thenReturn(
            ReadinessHealthResponse.builder()
                .status(OperationalStatus.DOWN)
                .service("qwenbridge")
                .apiVersion("v1")
                .checkedAt(Instant.parse("2026-07-04T11:00:00Z"))
                .dependencies(List.of(DependencyHealth.down("redis", "unavailable", 2)))
                .build());

    given()
        .when()
        .get("/api/v1/health/ready")
        .then()
        .statusCode(503)
        .body("status", equalTo("DOWN"))
        .body("dependencies[0].name", equalTo("redis"));
  }
}
