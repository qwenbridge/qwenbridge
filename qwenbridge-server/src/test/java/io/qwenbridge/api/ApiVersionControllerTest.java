package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.reset;

import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApiVersionControllerTest {

  @InjectMock AIService aiService;

  @InjectMock OpenSearchClient openSearchClient;

  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void resetMocks() {
    reset(aiService, openSearchClient, searchAnalysisService);
  }

  @Test
  void shouldReturnVersionInformation() {
    given()
        .when()
        .get("/api/v1/version")
        .then()
        .statusCode(200)
        .header("X-Request-ID", notNullValue())
        .header("X-QwenBridge-Version", "0.1.0-SNAPSHOT")
        .body("name", equalTo("qwenbridge"))
        .body("version", equalTo("0.1.0-SNAPSHOT"))
        .body("apiVersion", equalTo("v1"))
        .body("javaVersion", notNullValue());
  }
}
