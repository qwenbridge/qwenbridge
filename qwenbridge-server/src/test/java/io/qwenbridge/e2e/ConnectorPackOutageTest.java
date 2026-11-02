package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Section 4 Scenario C over HTTP: a Connector Pack is enabled while its backend is down (the
 * configured endpoint refuses connections). The connector reports FAILED with a reason that carries
 * no configuration values, the other connector of the pack stays DISABLED, and the Core keeps
 * serving. QB-287: QwenBridge ships no Connector Pack; the test-only {@code example} pack stands
 * in.
 */
@QuarkusTest
@TestProfile(ConnectorPackOutageTest.Outage.class)
class ConnectorPackOutageTest {

  static final String KEY = "test-only-connector-outage-key-1234";

  public static class Outage implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.connector-packs.enabled",
          "example",
          "qwenbridge.connector-packs.timeout",
          "2s",
          "qwenbridge.connector-packs.config",
          "{\"example-primary\":{\"settings\":{\"url\":\"http://127.0.0.1:9\"},"
              + "\"secrets\":{\"password\":\"env:QB_TEST_UNSET_OUTAGE_PASSWORD\"}}}",
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + KEY
              + "\",\"subject\":\"oz\",\"tenant\":\"outage\",\"roles\":[\"packs:read\",\"search\"]}]");
    }
  }

  @Test
  void aFailedConnectorPackIsReportedWhileTheCoreKeepsServing() {
    var packs =
        given()
            .header("Authorization", "Bearer " + KEY)
            .get("/api/v2/connector-packs")
            .then()
            .statusCode(200)
            .body("find { it.id == 'example' }.state", equalTo("FAILED"))
            .body(
                "find { it.id == 'example' }.connectors.find { it.id == 'example-primary' }.state",
                equalTo("FAILED"))
            .body(
                "find { it.id == 'example' }.connectors.find { it.id == 'example-secondary'"
                    + " }.state",
                equalTo("DISABLED"))
            .body(not(containsString("127.0.0.1:9")))
            .body(not(containsString("QB_TEST_UNSET_OUTAGE_PASSWORD")));
    given().get("/q/health/ready").then().statusCode(200);
    given().header("Authorization", "Bearer " + KEY).get("/api/v2/version").then().statusCode(200);
  }
}
