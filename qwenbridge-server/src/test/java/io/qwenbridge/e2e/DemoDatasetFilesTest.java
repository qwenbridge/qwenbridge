package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.commerce.DemoDatasets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-295: {@code qwenbridge.demo.dataset-files} loads a deterministic catalog file (here the
 * dual-SDK E2E catalog) into the in-process backend for the demo tenant, through the same canonical
 * pipeline as the bundled datasets. Embedding stays off (the default), so products are keyword
 * searchable only.
 */
@QuarkusTest
@TestProfile(DemoDatasetFilesTest.Files.class)
class DemoDatasetFilesTest {

  static final Path CATALOG = Path.of("../e2e/dual-sdk/fixtures/catalog-e2e-1.0.json");

  public static class Files implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<>(new ServiceFreeProfile().getConfigOverrides());
      c.put("qwenbridge.demo.datasets", "");
      c.put("qwenbridge.demo.dataset-files", CATALOG.toString());
      return c;
    }
  }

  @Test
  void aDatasetFileIsReadInTheBundledFormat() {
    var dataset = DemoDatasets.readFile(new ObjectMapper(), CATALOG);
    assertThat(dataset.id()).isEqualTo("e2e-dual-sdk");
    assertThat(dataset.connector()).isEqualTo("http-json");
    assertThat(dataset.records()).hasSize(24);
  }

  @Test
  void itsProductsAreSearchableForTheDemoTenant() {
    String body =
        given()
            .header("Authorization", "Bearer " + ServiceFreeProfile.KEY)
            .contentType("application/json")
            .body(Map.of("query", LocalModelStub.DATASET_FILE))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(body).contains("\"e2e-bag-01\"");
    // The bundled demo dataset is not loaded in this profile.
    assertThat(body).doesNotContain("demo-shoe-01");
  }
}
