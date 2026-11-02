package io.qwenbridge.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-259 over HTTP: the server starts with the file store (no Redis, database or marketplace) and
 * serves the local pack registry from its configured directory.
 */
@QuarkusTest
@TestProfile(OfflinePackRegistryApiTest.FileStore.class)
class OfflinePackRegistryApiTest {

  static final String KEY = "test-only-offline-packs-key-12345678";
  static final java.nio.file.Path DIR;

  static {
    try {
      DIR = Files.createTempDirectory("qwenbridge-offline-registry-");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class FileStore implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.platform.store",
          "file",
          "qwenbridge.platform.file-dir",
          DIR.toString(),
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + KEY
              + "\",\"subject\":\"olga\",\"tenant\":\"offline\",\"roles\":[\"packs:read\",\"search\"]}]");
    }
  }

  @Test
  void theRegistryIsServedFromLocalFiles() throws Exception {
    given()
        .header("Authorization", "Bearer " + KEY)
        .get("/api/v2/platform/packs")
        .then()
        .statusCode(200)
        .body("manifest.id", hasItems("commerce", "library"))
        .body("bundled", everyItem(equalTo(true)))
        .body("active", everyItem(equalTo(true)));
    given().get("/q/health/ready").then().statusCode(200);
    given()
        .header("Authorization", "Bearer " + KEY)
        .get("/api/v2/platform/license")
        .then()
        .statusCode(200)
        // QB-267, QB-269: no licence server and no licence: Community, nothing suspended.
        .body("edition", equalTo("COMMUNITY"))
        .body("state", equalTo("NONE"))
        .body("features", hasItems("search", "sessions", "builtin-packs"))
        .body("suspendedPacks.size()", equalTo(0));
    // Persistence across restarts on this store is covered by PackLifecycleTest.
    org.assertj.core.api.Assertions.assertThat(Files.isDirectory(DIR)).isTrue();
  }
}
