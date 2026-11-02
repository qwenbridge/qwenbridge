package io.qwenbridge.ai.provider.ollama;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables({
  @EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OPENSEARCH_IT", matches = "true")
})
class OllamaPipelineIntegrationTest {

  @Test
  void shouldAnalyzeSearchQueryUsingRealOllamaRewrite() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"wooden dining table\"}")
        .when()
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("originalQuery", equalTo("wooden dining table"))
        .body("decision", equalTo("ALLOW"))
        .body("rewrites[0]", not(blankOrNullString()))
        .body("policyPassed", equalTo(true));
  }

  @Test
  void contradictorySafeInputRequiresClarificationInsteadOfExecutingAnArbitrarySearch() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"Nike shoes size 41 and size 42\"}")
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLARIFY"))
        .body("clarificationQuestions", not(org.hamcrest.Matchers.empty()))
        .body("executionResult.executed", equalTo(false));
  }

  @Test
  void unsupportedModelOperatorAsksForClarificationWithoutBackendExecution() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"android flagship smartphone with bright display\"}")
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLARIFY"))
        .body("clarificationQuestions", not(org.hamcrest.Matchers.empty()))
        .body("executionResult.executed", equalTo(false));
  }
}
