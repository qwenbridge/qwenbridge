package io.qwenbridge.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-287: the independent Library domain pack runs through the same pack execution pipeline as
 * Commerce on the built-in in-process backend (this coverage previously ran only on the removed
 * Elasticsearch, PostgreSQL and MongoDB connectors): documents are normalized by the pack and
 * indexed per tenant, a query becomes grounded clauses and a filter, and another tenant sees none
 * of the documents.
 */
@QuarkusTest
@TestProfile(LibraryDomainPipelineTest.Profile.class)
class LibraryDomainPipelineTest {

  static final String A = "test-only-library-tenant-a-key-12345";
  static final String B = "test-only-library-tenant-b-key-12345";

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<String, String>();
      c.put("qwenbridge.commerce.backend", "memory");
      c.put("qwenbridge.security.authentication-required", "true");
      c.put(
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + A
              + "\",\"subject\":\"a\",\"tenant\":\"library-a\",\"roles\":[\"search\",\"catalog:write\"]},"
              + "{\"key\":\""
              + B
              + "\",\"subject\":\"b\",\"tenant\":\"library-b\",\"roles\":[\"search\",\"catalog:write\"]}]");
      return c;
    }
  }

  static io.restassured.specification.RequestSpecification as(String key) {
    return given().header("Authorization", "Bearer " + key).contentType("application/json");
  }

  static void ingest(String key, String id, Map<String, Object> data) {
    as(key)
        .body(Map.of("id", id, "data", data, "backend", "memory"))
        .post("/api/v2/domains/library/documents")
        .then()
        .statusCode(200);
  }

  @Test
  void booksAreIndexedPerTenantAndQueriesBecomeGroundedFilters() {
    ingest(A, "book-1", Map.of("title", "A book about systems", "author", "Ada", "year", 2024));
    ingest(A, "book-old", Map.of("title", "An older book", "author", "Ada", "year", 2010));
    ingest(A, "book-other", Map.of("title", "Another author", "author", "Grace", "year", 2023));
    Map<String, Object> query =
        Map.of("query", "books by Ada after 2020", "backend", "memory", "mode", "KEYWORD");
    as(A)
        .body(query)
        .post("/api/v2/domains/library/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("understanding.clauses", hasSize(2))
        .body("understanding.clauses[0].quote", equalTo("by Ada"))
        .body("documents", hasSize(1))
        .body("documents[0].author", equalTo("Ada"))
        .body("documents[0].year", equalTo(2024));
    as(B)
        .body(query)
        .post("/api/v2/domains/library/search")
        .then()
        .statusCode(200)
        .body("documents", empty());
  }

  @Test
  void anInvalidBookIsRefusedBeforeIndexing() {
    as(A)
        .body(Map.of("id", "bad", "data", Map.of("title", "No author"), "backend", "memory"))
        .post("/api/v2/domains/library/documents")
        .then()
        .statusCode(400);
  }
}
