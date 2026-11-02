package io.qwenbridge.extensions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-232, QB-238, QB-240, QB-241 over HTTP: the server starts and serves with every optional
 * Connector Pack disabled, and both pack inventories are discoverable with {@code packs:read}.
 */
@QuarkusTest
@TestProfile(PacksApiTest.Principals.class)
class PacksApiTest {

  static final String ADMIN = "test-only-packs-admin-12345678901";
  static final String SEARCHER = "test-only-packs-search-1234567890";

  public static class Principals implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + ADMIN
              + "\",\"subject\":\"ada\",\"tenant\":\"acme\",\"roles\":[\"packs:read\"]},"
              + "{\"key\":\""
              + SEARCHER
              + "\",\"subject\":\"sam\",\"tenant\":\"acme\",\"roles\":[\"search\"]}]");
    }
  }

  @Test
  void connectorPacksAreDiscoverableAndAllDisabledByDefault() {
    given()
        .header("Authorization", "Bearer " + ADMIN)
        .get("/api/v2/connector-packs")
        .then()
        .statusCode(200)
        // QB-287: no Connector Pack ships with QwenBridge 1.0; the test-only example pack is
        // discovered through the same ServiceLoader contract and stays disabled.
        .body("id", containsInAnyOrder("example"))
        .body("state", everyItem(equalTo("DISABLED")))
        .body("flatten { it.systems }", hasItems("example-store", "example-index"))
        .body(
            "find { it.id == 'example' }.connectors.find { it.id == 'example-primary' }"
                + ".declared.access",
            containsInAnyOrder("READ"))
        .body(
            "find { it.id == 'example' }.connectors.find { it.id == 'example-secondary' }"
                + ".declared.access",
            hasItems("READ", "WRITE", "DELETE"))
        .body("find { it.id == 'example' }.connectors[0].reason", equalTo("pack disabled"))
        .body(not(containsString("password")));
    // The Core keeps serving with no connector pack enabled.
    given().get("/q/health/ready").then().statusCode(200);
  }

  @Test
  void domainPacksShowWhatTheyRegistered() {
    given()
        .header("Authorization", "Bearer " + ADMIN)
        .get("/api/v2/domain-packs")
        .then()
        .statusCode(200)
        .body("packId", containsInAnyOrder("commerce", "library"))
        .body("find { it.packId == 'commerce' }.extension.namespace", equalTo("commerce"))
        .body("find { it.packId == 'library' }.extension.readableVersions", hasItems("1.0"));
  }

  @Test
  void inventoriesNeedPacksRead() {
    for (String path : new String[] {"/api/v2/connector-packs", "/api/v2/domain-packs"}) {
      given().header("Authorization", "Bearer " + SEARCHER).get(path).then().statusCode(403);
      given().get(path).then().statusCode(403);
    }
  }
}
