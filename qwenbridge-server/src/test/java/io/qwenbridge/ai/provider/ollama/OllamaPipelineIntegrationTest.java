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
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class OllamaPipelineIntegrationTest {

  @Test
  void shouldAnalyzeSearchQueryUsingRealOllamaRewrite() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"tabel\"}")
        .when()
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("originalQuery", equalTo("tabel"))
        .body("decision", equalTo("ALLOW"))
        .body("rewrites[0]", not(blankOrNullString()))
        .body("policyPassed", equalTo(true));
  }
}
