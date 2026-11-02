package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/** QB-006: stable, safe request and correlation identifiers on every response. */
@QuarkusTest
class CorrelationIdApiTest {

  @Test
  void validRequestAndCorrelationIdsArePreserved() {
    given()
        .header("X-Request-ID", "client-req-1")
        .header("X-Correlation-ID", "conversation:42")
        .get("/api/v1/health")
        .then()
        .statusCode(200)
        .header("X-Request-ID", "client-req-1")
        .header("X-Correlation-ID", "conversation:42");
  }

  @Test
  void correlationIdDefaultsToRequestId() {
    Response response = given().header("X-Request-ID", "solo-1").get("/api/v1/health");
    assertThat(response.header("X-Correlation-ID")).isEqualTo("solo-1");
  }

  @Test
  void generatedIdsAreReturnedWhenNoneSupplied() {
    Response response = given().get("/api/v1/health");
    assertThat(response.header("X-Request-ID")).matches("[0-9a-f-]{36}");
    assertThat(response.header("X-Correlation-ID")).isEqualTo(response.header("X-Request-ID"));
  }

  @Test
  void unsafeRequestIdIsReplacedAndNeverEchoed() {
    String unsafe = "evil<script>alert(1)</script>";
    Response response = given().header("X-Request-ID", unsafe).get("/api/v1/health");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.header("X-Request-ID")).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    assertThat(response.asString()).doesNotContain("<script>");
  }

  @Test
  void errorResponsesCarryTheResolvedRequestId() {
    String unsafe = "x".repeat(300);
    Response response =
        given()
            .header("X-Request-ID", unsafe)
            .contentType(ContentType.JSON)
            .body("{\"query\":\"\"}")
            .post("/api/v1/search/analyze");
    assertThat(response.statusCode()).isEqualTo(400);
    String headerId = response.header("X-Request-ID");
    assertThat(headerId).hasSizeLessThanOrEqualTo(128).isNotEqualTo(unsafe);
    response.then().body("requestId", equalTo(headerId));
  }
}
