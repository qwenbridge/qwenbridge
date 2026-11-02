package io.qwenbridge.spelling;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.e2e.ServiceFreeProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-291 over REST and SSE: application settings (en, sv, fa, plus a language without a provider
 * and an invalid identifier), and three tenants with their own settings. Each tenant sees only its
 * own configuration and corrections; the original query is always returned. Dictionaries are the
 * test fixtures in {@code src/test/resources/typo}.
 */
@QuarkusTest
@TestProfile(SpellingApiTest.Typo.class)
class SpellingApiTest {

  static final String APP = "test-only-spelling-app-key-1234567";
  static final String NORDIC = "test-only-spelling-nordic-key-12345";
  static final String PERSIAN = "test-only-spelling-persian-key-1234";
  static final String OFF = "test-only-spelling-off-key-12345678";

  public static class Typo implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<>(new ServiceFreeProfile().getConfigOverrides());
      c.put("qwenbridge.typo.enabled", "true");
      c.put("qwenbridge.typo.languages", "en,sv,fa,de,not_a_tag!");
      c.put("qwenbridge.typo.mode", "SAFE_AUTO_CORRECT");
      c.put("qwenbridge.typo.dictionary-dir", "src/test/resources/typo");
      c.put(
          "qwenbridge.typo.tenants",
          "{\"nordic\":{\"enabled\":true,\"languages\":[\"sv\"],\"mode\":\"SAFE_AUTO_CORRECT\"},"
              + "\"persian\":{\"enabled\":true,\"languages\":[\"fa\"],\"mode\":\"SUGGEST_ONLY\"},"
              + "\"off\":{\"enabled\":false}}");
      c.put(
          "qwenbridge.security.api-principals",
          "["
              + principal(APP, "shopA")
              + ","
              + principal(NORDIC, "nordic")
              + ","
              + principal(PERSIAN, "persian")
              + ","
              + principal(OFF, "off")
              + "]");
      return c;
    }

    static String principal(String key, String tenant) {
      return "{\"key\":\""
          + key
          + "\",\"subject\":\""
          + tenant
          + "-user\",\"tenant\":\""
          + tenant
          + "\",\"roles\":[\"search\"]}";
    }
  }

  static io.restassured.specification.RequestSpecification as(String key) {
    return given().header("Authorization", "Bearer " + key).contentType("application/json");
  }

  static io.restassured.path.json.JsonPath search(String key, String query) {
    return as(key)
        .body(Map.of("query", query))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath();
  }

  @Test
  void eachTenantSeesItsOwnConfigurationAndLanguageStatus() {
    var app = as(APP).get("/api/v2/spelling").then().statusCode(200).extract().jsonPath();
    assertThat(app.getString("scope")).isEqualTo("application");
    assertThat(app.getString("mode")).isEqualTo("SAFE_AUTO_CORRECT");
    Map<String, String> statuses = new HashMap<>();
    for (Map<String, Object> l : app.<Map<String, Object>>getList("languages"))
      statuses.put(String.valueOf(l.get("configured")), String.valueOf(l.get("status")));
    assertThat(statuses)
        .containsEntry("en", "SUPPORTED")
        .containsEntry("sv", "SUPPORTED")
        .containsEntry("fa", "SUPPORTED")
        .containsEntry("de", "PROVIDER_UNAVAILABLE")
        .containsEntry("not_a_tag!", "INVALID_IDENTIFIER");
    assertThat(app.getList("providers.id")).containsExactly("dictionary");
    assertThat(app.<String>getList("providers[0].capabilities"))
        .contains("DICTIONARY", "DOMAIN_VOCABULARY", "PHRASES", "FREQUENCIES");
    var nordic = as(NORDIC).get("/api/v2/spelling").then().statusCode(200).extract().jsonPath();
    assertThat(nordic.getString("scope")).isEqualTo("tenant");
    assertThat(nordic.getList("languages.configured")).containsExactly("sv");
    var off = as(OFF).get("/api/v2/spelling").then().statusCode(200).extract().jsonPath();
    assertThat(off.getBoolean("enabled")).isFalse();
    assertThat(off.getString("mode")).isEqualTo("DISABLED");
  }

  @Test
  void correctionsFollowTheCallersTenantAndProtectInformation() {
    var app = search(APP, "blak shoes size 42 under 100 EUR redis03-pt1");
    assertThat(app.getString("spelling.status")).isEqualTo("CORRECTED");
    assertThat(app.getString("spelling.original"))
        .isEqualTo("blak shoes size 42 under 100 EUR redis03-pt1");
    assertThat(app.getString("spelling.corrected"))
        .isEqualTo("black shoes size 42 under 100 EUR redis03-pt1");
    assertThat(app.getInt("spelling.protectedTokens")).isGreaterThanOrEqualTo(2);
    assertThat(app.getBoolean("spelling.corrections[0].applied")).isTrue();

    // Swedish only: the Swedish typo is corrected for this tenant ...
    var nordic = search(NORDIC, "röd jakca");
    assertThat(nordic.getString("spelling.corrected")).isEqualTo("röd jacka");
    assertThat(nordic.getList("spelling.activeLanguages")).containsExactly("sv");
    // ... but an English typo is not, although another tenant has English.
    var nordicEnglish = search(NORDIC, "blak shoes");
    assertThat(nordicEnglish.getString("spelling.corrected")).isEqualTo("blak shoes");

    // Persian with suggestions only: suggested, never applied.
    var persian = search(PERSIAN, "کفس مشکی");
    assertThat(persian.getString("spelling.status")).isEqualTo("SUGGESTED");
    assertThat(persian.getString("spelling.corrected")).isEqualTo("کفس مشکی");
    assertThat(persian.getString("spelling.corrections[0].replacement")).isEqualTo("کفش");
    // Latin text is outside the Persian provider's script: no correction.
    assertThat(search(PERSIAN, "röd jakca").getString("spelling.corrected")).isEqualTo("röd jakca");

    // Typo correction disabled for this tenant: the response carries no spelling at all.
    String off =
        as(OFF)
            .body(Map.of("query", "blak shoes"))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(off).doesNotContain("\"spelling\"");
  }

  @Test
  void theSseTerminalEventCarriesTheSpellingResult() {
    as(APP)
        .body(Map.of("query", "blak shoes"))
        .post("/api/v2/search/stream")
        .then()
        .statusCode(200)
        .body(
            containsString("event:search.completed"),
            containsString("\"spelling\":{\"status\":\"CORRECTED\""),
            containsString("\"corrected\":\"black shoes\""));
    as(OFF)
        .body(Map.of("query", "blak shoes"))
        .post("/api/v2/search/stream")
        .then()
        .statusCode(200)
        .body(not(containsString("\"spelling\"")));
  }

  @Test
  void concurrentTenantsDoNotInterfere() throws Exception {
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
      List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
      for (int i = 0; i < 12; i++) {
        String key = i % 2 == 0 ? NORDIC : APP;
        results.add(
            pool.submit(
                () -> key + "=" + search(key, "blak shoes").getString("spelling.corrected")));
      }
      for (var r : results) assertThat(r.get()).isIn(NORDIC + "=blak shoes", APP + "=black shoes");
    }
  }
}
