package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.commerce.DemoDatasets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-203, QB-205: complete request-to-response workflows with no Redis, PostgreSQL, MongoDB,
 * Elasticsearch, OpenSearch or Shopify (all unreachable or unconfigured) and a controlled local
 * model. Covers validation, model analysis and grounding, QueryIR, planning, local retrieval on the
 * synthetic dataset, ranking, evidence, analytics-backed refinement, sessions, streaming, tenant
 * isolation and the error paths. Expected results come from the dataset file, not from this test.
 */
@QuarkusTest
@TestProfile(ServiceFreeProfile.class)
class ServiceFreeE2ETest {
  static final ObjectMapper JSON = new ObjectMapper();

  static List<String> expected(String name) throws Exception {
    var root =
        JSON.readTree(
            DemoDatasets.class
                .getClassLoader()
                .getResourceAsStream("datasets/commerce-demo-1.0.json"));
    for (var e : root.path("expectations"))
      if (e.path("name").asText().equals(name))
        return JSON.convertValue(
            e.path("expected"),
            JSON.getTypeFactory().constructCollectionType(List.class, String.class));
    throw new IllegalStateException(name);
  }

  static io.restassured.specification.RequestSpecification as(String key) {
    return given().header("Authorization", "Bearer " + key).contentType("application/json");
  }

  @Test
  void aSearchReturnsExactlyTheDatasetsExpectedProductsWithGroundedQueryIrAndEvidence()
      throws Exception {
    var response =
        as(ServiceFreeProfile.KEY)
            .body(Map.of("query", LocalModelStub.EVERY, "requestId", "e2e-every-1"))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .body("status", equalTo("OK"))
            .extract()
            .jsonPath();
    assertThat(response.<String>getList("results.hits.id"))
        .containsExactlyInAnyOrderElementsOf(expected("every-constraint"));
    assertThat(response.<String>getList("queryIR.constraints.field"))
        .contains("brand", "color", "size", "price", "currency", "condition", "inStock");
    // Every constraint is grounded in the user's own words.
    for (var quote : response.<String>getList("queryIR.constraints.evidence.quote"))
      assertThat(LocalModelStub.EVERY).contains(quote);
    assertThat(response.getMap("timings")).containsKeys("analysis", "execution");
    String evidenceId = response.getString("evidence[0].id");
    as(ServiceFreeProfile.KEY).get("/api/v2/evidence/" + evidenceId).then().statusCode(200);
    // Another tenant sees neither the products nor the evidence.
    as(ServiceFreeProfile.OTHER)
        .body(Map.of("query", LocalModelStub.EVERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("results.totalHits", equalTo(0));
    as(ServiceFreeProfile.OTHER).get("/api/v2/evidence/" + evidenceId).then().statusCode(404);
  }

  @Test
  void thePlannedContractRecordsItsPlanAndRetrievesLocally() throws Exception {
    var response =
        as(ServiceFreeProfile.KEY)
            .body(Map.of("query", LocalModelStub.EVERY, "contract", "commerce-search/1.0"))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    assertThat(response.getString("interpretation.queryIrVersion")).isEqualTo("1.0");
    assertThat(response.getString("retrieval.executed")).isNotBlank();
    assertThat(response.<String>getList("retrieval.providers")).isNotEmpty();
    // The planned contract's exclusion semantics differ from apiVersion 2 (see the dataset).
    assertThat(response.<String>getList("results.hits.id"))
        .containsExactlyInAnyOrderElementsOf(expected("every-constraint-planned"));
  }

  @Test
  void refinementStreamingAndSessionsWorkWithInProcessStores() throws Exception {
    as(ServiceFreeProfile.KEY)
        .body(Map.of("query", LocalModelStub.EVERY, "requestId", "e2e-refine-base"))
        .post("/api/v2/search")
        .then()
        .statusCode(200);
    as(ServiceFreeProfile.KEY)
        .body(Map.of("query", LocalModelStub.BACKPACK))
        .post("/api/v2/search/e2e-refine-base/refine")
        .then()
        .statusCode(200);
    String stream =
        as(ServiceFreeProfile.KEY)
            .body(Map.of("query", LocalModelStub.BACKPACK))
            .post("/api/v2/search/stream")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(stream).contains("event:search.started", "event:search.completed", "demo-bag-01");
    String session =
        as(ServiceFreeProfile.KEY)
            .body(Map.of())
            .post("/api/v2/sessions")
            .then()
            .statusCode(200)
            .extract()
            .path("id");
    as(ServiceFreeProfile.KEY)
        .body(Map.of("query", LocalModelStub.BACKPACK, "expectedRevision", 0))
        .post("/api/v2/sessions/" + session + "/turns")
        .then()
        .statusCode(200)
        .body("revision", equalTo(1))
        .body(
            "last.results.hits.id",
            org.hamcrest.Matchers.contains(expected("brand-and-category").toArray()));
  }

  @Test
  void errorPathsFailClosedAndTyped() {
    as(ServiceFreeProfile.KEY)
        .body(Map.of("query", LocalModelStub.HOSTILE))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("BLOCKED"))
        .body("results.totalHits", equalTo(0));
    // The model is unavailable for this query: no guessing, the request goes to review.
    as(ServiceFreeProfile.KEY)
        .body(Map.of("query", "something the local model was never told about"))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("REVIEW"));
    as(ServiceFreeProfile.KEY)
        .header("X-Request-Timeout-Ms", "never")
        .body(Map.of("query", LocalModelStub.EVERY))
        .post("/api/v2/search")
        .then()
        .statusCode(400)
        .body("category", equalTo("VALIDATION"));
    given()
        .contentType("application/json")
        .body(Map.of("query", "x"))
        .post("/api/v2/search")
        .then()
        .statusCode(401);
  }
}
