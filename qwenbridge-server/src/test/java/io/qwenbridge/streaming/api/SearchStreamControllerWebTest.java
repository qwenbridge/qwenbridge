package io.qwenbridge.streaming.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SearchStreamControllerWebTest {

  @Inject StreamingSessionRegistry registry;

  @AfterEach
  void tearDown() {
    registry.clear();
  }

  @Test
  void shouldRegisterStableConnectedEventEnvelopeSession() {
    registry.register("request-1");

    assertThat(registry.findByRequestId("request-1")).hasSize(1);
  }

  @Test
  void shouldReturnApiErrorForUnsupportedStreamRequestId() {
    given()
        .when()
        .get("/api/v1/search/stream/request@1")
        .then()
        .statusCode(400)
        .contentType("application/json")
        .body("status", equalTo(400))
        .body("error", equalTo("Bad Request"))
        .body("code", equalTo("BAD_REQUEST"))
        .body("message", equalTo("requestId contains unsupported characters"))
        .body("path", endsWith("api/v1/search/stream/request@1"))
        .body("requestId", notNullValue())
        .header("X-Request-ID", notNullValue())
        .header("X-QwenBridge-Version", notNullValue());

    assertThat(registry.size()).isZero();
  }

  @Test
  void shouldReturnApiErrorForRequestIdLongerThanMaximumLength() {
    String requestId = "a".repeat(129);

    given()
        .when()
        .get("/api/v1/search/stream/{requestId}", requestId)
        .then()
        .statusCode(400)
        .contentType("application/json")
        .body("status", equalTo(400))
        .body("code", equalTo("BAD_REQUEST"))
        .body("message", equalTo("requestId must not exceed 128 characters"))
        .body("requestId", notNullValue());

    assertThat(registry.size()).isZero();
  }
}
