package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Public endpoint snapshot (QB-179, QB-185). The served OpenAPI document must list exactly the
 * endpoints in {@code src/test/resources/contract/api-endpoints.txt}:
 *
 * <ul>
 *   <li>an endpoint missing from the server is a breaking change: it needs a new API version and
 *       migration guidance (docs/api/rest-api.md), never a silent removal;
 *   <li>an endpoint missing from the snapshot is an unreviewed addition: document it, then add it.
 * </ul>
 *
 * The actual list is written to {@code target/contract/api-endpoints.txt} for review.
 */
@QuarkusTest
class ApiEndpointSnapshotTest {

  static final Path SNAPSHOT = Path.of("src", "test", "resources", "contract", "api-endpoints.txt");
  static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

  @Test
  void theServedEndpointsMatchTheReviewedSnapshot() throws Exception {
    var document =
        new ObjectMapper()
            .readTree(
                given()
                    .accept("application/json")
                    .get("/v3/api-docs?format=json")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString());
    var actual = new TreeSet<String>();
    document
        .get("paths")
        .properties()
        .forEach(
            path ->
                path.getValue()
                    .fieldNames()
                    .forEachRemaining(
                        method -> {
                          if (METHODS.contains(method))
                            actual.add(
                                method.toUpperCase(java.util.Locale.ROOT) + " " + path.getKey());
                        }));
    Files.createDirectories(Path.of("target", "contract"));
    Files.write(Path.of("target", "contract", "api-endpoints.txt"), actual);

    var expected = new TreeSet<String>();
    for (String line : Files.readAllLines(SNAPSHOT))
      if (!line.isBlank() && !line.startsWith("#")) expected.add(line.strip());

    var removed = new TreeSet<>(expected);
    removed.removeAll(actual);
    var added = new TreeSet<>(actual);
    added.removeAll(expected);
    assertThat(removed)
        .as("endpoints removed without a new API version and migration guidance")
        .isEmpty();
    assertThat(added).as("endpoints added without updating the reviewed snapshot").isEmpty();
  }
}
