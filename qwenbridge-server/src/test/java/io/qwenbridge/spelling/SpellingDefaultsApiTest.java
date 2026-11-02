package io.qwenbridge.spelling;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * QB-291 A3: with no typo configuration nothing is enabled, no language is assumed, startup works.
 */
@QuarkusTest
class SpellingDefaultsApiTest {

  @Test
  void nothingIsEnabledByDefault() {
    given()
        .get("/api/v2/spelling")
        .then()
        .statusCode(200)
        .body("enabled", equalTo(false))
        .body("mode", equalTo("DISABLED"))
        .body("languages", empty())
        .body("providers", empty());
  }
}
