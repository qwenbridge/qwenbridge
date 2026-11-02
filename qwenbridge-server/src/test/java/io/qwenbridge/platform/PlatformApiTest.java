package io.qwenbridge.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.*;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.commerce.*;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.util.*;
import org.junit.jupiter.api.*;

@QuarkusTest
@TestProfile(PlatformApiTest.Keys.class)
class PlatformApiTest {
  static final String ADMIN = "table1-admin-test-only-credential",
      BOB = "table1-bob-test-only-credential",
      OTHER = "table1-other-test-only-credential";

  public static class Keys implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.security.authentication-required",
          "true",
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + ADMIN
              + "\",\"subject\":\"alice\",\"tenant\":\"apiTable1\",\"roles\":[\"search\",\"catalog:write\",\"packs:read\",\"packs:write\",\"license:write\",\"marketplace:publish\"]},{\"key\":\""
              + BOB
              + "\",\"subject\":\"bob\",\"tenant\":\"apiTable1\",\"roles\":[\"search\"]},{\"key\":\""
              + OTHER
              + "\",\"subject\":\"alice\",\"tenant\":\"apiTable2\",\"roles\":[\"search\"]}]");
    }
  }

  @InjectMock CommerceSearchService search;

  @BeforeEach
  void setup() {
    when(search.search(any(), any()))
        .thenAnswer(
            i -> {
              var q = (SearchRequest) i.getArgument(0);
              return new SearchResponse(
                  2,
                  "api-result",
                  "OK",
                  new Translation(1, q.query(), "en", "en", q.query(), true, List.of()),
                  "SEARCH",
                  null,
                  List.of(),
                  SearchResultSet.empty(),
                  null,
                  List.of(),
                  List.of(),
                  "none",
                  0);
            });
  }

  io.restassured.specification.RequestSpecification auth(String credential) {
    return given().header("Authorization", "Bearer " + credential).contentType("application/json");
  }

  @Test
  void ownedSessionSurvivesRestAndSseAndCannotBeReadByAnotherUserOrTenant() {
    String id =
        auth(ADMIN)
            .body(Map.of("ttlSeconds", 600, "lockedFields", List.of("price")))
            .post("/api/v2/sessions")
            .then()
            .statusCode(200)
            .body("revision", equalTo(0))
            .extract()
            .path("id");
    auth(BOB).get("/api/v2/sessions/" + id).then().statusCode(404);
    auth(OTHER).get("/api/v2/sessions/" + id).then().statusCode(404);
    auth(ADMIN)
        .body(Map.of("query", "black shoes under 100 EUR", "expectedRevision", 0))
        .post("/api/v2/sessions/" + id + "/turns")
        .then()
        .statusCode(200)
        .body("revision", equalTo(1));
    auth(ADMIN)
        .body(Map.of("query", "new search", "expectedRevision", 1, "resetContext", true))
        .post("/api/v2/sessions/" + id + "/turns/stream")
        .then()
        .statusCode(200)
        .body(containsString("session.started"))
        .body(containsString("session.completed"));
    auth(ADMIN)
        .get("/api/v2/sessions/" + id)
        .then()
        .statusCode(200)
        .body("revision", equalTo(2))
        .body("turns.size()", equalTo(2));
    auth(ADMIN)
        .body(Map.of("query", "stale", "expectedRevision", 0))
        .post("/api/v2/sessions/" + id + "/turns")
        .then()
        .statusCode(409);
  }

  @Test
  void packLicenseAndCatalogMutationsRequireVerifiedPermissionsAndEntitlements() {
    auth(BOB).body(Map.of()).post("/api/v2/platform/packs").then().statusCode(403);
    auth(BOB).delete("/api/v2/platform/license").then().statusCode(403);
    auth(ADMIN)
        .get("/api/v2/platform/editions")
        .then()
        .statusCode(200)
        .body("PROFESSIONAL", hasItem("external-packs"));
    auth(ADMIN)
        .get("/api/v2/platform/license")
        .then()
        .statusCode(200)
        .body("edition", equalTo("COMMUNITY"));
    auth(ADMIN).body(Map.of()).post("/api/v2/platform/marketplace").then().statusCode(403);
    auth(ADMIN)
        .get("/api/v2/platform/packs")
        .then()
        .statusCode(200)
        .body("manifest.id", hasItems("commerce", "library"));
    auth(BOB)
        .body(Map.of("id", "book-1", "data", Map.of("title", "A book", "author", "Ada")))
        .post("/api/v2/domains/library/normalize")
        .then()
        .statusCode(403);
    auth(ADMIN)
        .body(
            Map.of(
                "id", "book-1", "data", Map.of("title", "A book", "author", "Ada", "year", 2024)))
        .post("/api/v2/domains/library/normalize")
        .then()
        .statusCode(200)
        .body("[0].domain", equalTo("library"))
        .body("[0].tenantId", equalTo("apiTable1"));
    given()
        .body("{}")
        .contentType("application/json")
        .post("/api/v2/sessions")
        .then()
        .statusCode(401);
  }
}
