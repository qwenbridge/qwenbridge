package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-287: catalog synchronization is the one feature that needs Redis (its tenant checkpoint and
 * lock). Without Redis the server still starts and serves, and a synchronization request is refused
 * with an explicit, typed 503 instead of an unexplained server error.
 */
@QuarkusTest
@TestProfile(CatalogSyncWithoutRedisTest.Profile.class)
class CatalogSyncWithoutRedisTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<>(new ServiceFreeProfile().getConfigOverrides());
      c.put("qwenbridge.catalog.endpoint", "http://127.0.0.1:9/feed");
      c.put(
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + ServiceFreeProfile.KEY
              + "\",\"subject\":\"ops\",\"tenant\":\"shopA\",\"roles\":[\"search\",\"catalog:write\"]}]");
      return c;
    }
  }

  @Test
  void synchronizationExplainsThatItNeedsRedis() {
    given().get("/q/health/ready").then().statusCode(200);
    given()
        .header("Authorization", "Bearer " + ServiceFreeProfile.KEY)
        .contentType("application/json")
        .body("{}")
        .post("/api/v2/catalog/sync")
        .then()
        .statusCode(503)
        .body("category", equalTo("DEPENDENCY"))
        .body("message", containsString("needs Redis"));
  }
}
