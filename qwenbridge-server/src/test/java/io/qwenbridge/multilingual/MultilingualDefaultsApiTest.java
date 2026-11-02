package io.qwenbridge.multilingual;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * QB-286: with no multilingual configuration nothing is enabled, no target language is assumed and
 * no provider is active; startup works.
 */
@QuarkusTest
class MultilingualDefaultsApiTest {

  @Test
  void nothingIsEnabledByDefault() {
    given()
        .get("/api/v2/multilingual")
        .then()
        .statusCode(200)
        .body("enabled", equalTo(false))
        .body("policy", equalTo("ALL_TARGETS"))
        .body("targets", empty())
        .body("providers", empty())
        .body("aliases.SE", equalTo("sv"));
  }
}
