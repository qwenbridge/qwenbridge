package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-293 (Redis as an optional cache): with no platform store configured, Commerce evidence and
 * analytics use Redis as their short-lived store. Here Redis is unreachable (port 9 refuses
 * connections), and search, evidence lookup and click analytics keep working through the in-process
 * fallback instead of failing. Platform state stays in memory, never in Redis.
 */
@QuarkusTest
@TestProfile(RedisOutageFallbackTest.RedisDown.class)
class RedisOutageFallbackTest {

  public static class RedisDown implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<>(new ServiceFreeProfile().getConfigOverrides());
      // No platform store: evidence and analytics take the Redis path, and Redis is down.
      c.remove("qwenbridge.platform.store");
      return c;
    }
  }

  static io.restassured.specification.RequestSpecification as() {
    return given()
        .header("Authorization", "Bearer " + ServiceFreeProfile.KEY)
        .contentType("application/json");
  }

  @Test
  void searchEvidenceAndAnalyticsSurviveARedisOutage() {
    var response =
        as().body(Map.of("query", LocalModelStub.EVERY, "requestId", "redis-down-1"))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .body("status", equalTo("OK"))
            .extract()
            .jsonPath();
    assertThat(response.getList("results.hits.id")).isNotEmpty();
    String evidence = response.getString("evidence[0].id");
    as().get("/api/v2/evidence/" + evidence).then().statusCode(200);
    String product = response.getString("results.hits[0].id");
    as().body(Map.of("requestId", "redis-down-1", "productId", product))
        .post("/api/v2/analytics/click")
        .then()
        .statusCode(200)
        .body("status", equalTo("recorded"));
    // A duplicate request id is still refused while Redis is down.
    as().body(Map.of("query", LocalModelStub.EVERY, "requestId", "redis-down-1"))
        .post("/api/v2/search")
        .then()
        .statusCode(409);
  }
}
