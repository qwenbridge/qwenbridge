package io.qwenbridge.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Section 4 Scenarios A and L, QB-282: a clean installation with no PostgreSQL, MongoDB, Redis,
 * Elasticsearch, OpenSearch, Shopify, license server or model server (every one unreachable or
 * unconfigured), the Commerce domain pack disabled, no Connector Pack, plugin, agent or Policy
 * Pack. The server starts, reports exactly that, and serves the documented baseline: a domain pack
 * with its rule grammar over local in-process retrieval, the local pack registry and Community
 * licensing.
 */
@QuarkusTest
@TestProfile(CleanStartTest.Clean.class)
class CleanStartTest {

  static final String KEY = "test-only-clean-start-key-1234567";

  public static class Clean implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<String, String>();
      try {
        c.put(
            "qwenbridge.platform.file-dir",
            Files.createTempDirectory("qwenbridge-clean-").toString());
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
      // Every external service unreachable (port 9 refuses connections) or unconfigured.
      c.put("quarkus.redis.hosts", "redis://127.0.0.1:9");
      c.put("qwenbridge.ai.ollama.base-url", "http://127.0.0.1:9");
      c.put("qwenbridge.search.opensearch.base-url", "http://127.0.0.1:9");
      c.put("qwenbridge.platform.store", "file");
      c.put("qwenbridge.commerce.backend", "memory");
      c.put("qwenbridge.embedding.enabled", "false");
      c.put("qwenbridge.analysis.cache.enabled", "false");
      c.put("qwenbridge.abuse.protection.redis.enabled", "false");
      // Optional packs off: the Commerce domain pack, every Connector Pack (the default) and no
      // plugins, agents or Policy Packs (none are installed).
      c.put("qwenbridge.domains.disabled", "commerce");
      c.put("qwenbridge.security.authentication-required", "true");
      c.put(
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + KEY
              + "\",\"subject\":\"cleo\",\"tenant\":\"clean\",\"roles\":"
              + "[\"search\",\"catalog:write\",\"packs:read\",\"plugins:read\",\"agents:read\"]}]");
      return c;
    }
  }

  static io.restassured.specification.RequestSpecification as() {
    return given().header("Authorization", "Bearer " + KEY).contentType("application/json");
  }

  @Test
  void theServerStartsAndReportsItsMinimalConfiguration() {
    given().get("/q/health/ready").then().statusCode(200);
    // The operational readiness names what is missing instead of hiding it.
    given()
        .get("/api/v1/health/ready")
        .then()
        .statusCode(200)
        .body("status", equalTo("DEGRADED"))
        .body("dependencies.find { it.name == 'redis' }.status", equalTo("DEGRADED"));
    as().get("/api/v2/version").then().statusCode(200);
    as().get("/api/v2/domain-packs").then().statusCode(200).body("packId", contains("library"));
    as().get("/api/v2/connector-packs")
        .then()
        .statusCode(200)
        .body("state", everyItem(equalTo("DISABLED")));
    as().get("/api/v2/extensions").then().statusCode(200).body("$", empty());
    as().get("/api/v2/agents").then().statusCode(200).body("agents", empty());
    as().get("/api/v2/platform/license")
        .then()
        .statusCode(200)
        .body("edition", equalTo("COMMUNITY"))
        .body("state", equalTo("NONE"));
  }

  @Test
  void aDomainPackSearchesLocallyWithNoModelDatabaseOrSearchEngine() {
    for (var book :
        new Object[][] {
          {"b1", "Notes on engines", "Ada Lovelace", 1843},
          {"b2", "Thinking machines", "Alan Turing", 1950},
          {"b3", "On computable numbers", "Alan Turing", 1936}
        })
      as().body(
              Map.of(
                  "id",
                  book[0],
                  "backend",
                  "memory",
                  "data",
                  Map.of("title", book[1], "author", book[2], "year", book[3], "available", true)))
          .post("/api/v2/domains/library/documents")
          .then()
          .statusCode(200);
    as().body(Map.of("query", "books by Alan Turing after 1940", "backend", "memory"))
        .post("/api/v2/domains/library/search")
        .then()
        .statusCode(200)
        .body("understanding.clauses.field", containsInAnyOrder("author", "year"))
        .body("documents.id", contains("b2"));
  }
}
