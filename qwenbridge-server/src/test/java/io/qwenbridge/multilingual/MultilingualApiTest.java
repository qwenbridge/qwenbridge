package io.qwenbridge.multilingual;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.e2e.ServiceFreeProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * QB-286 over REST and SSE with the real local glossary provider (test phrase tables in {@code
 * src/test/resources/multilingual/glossary}: en→sv, en→fa, en→nb, en→nn, sv→en). The application
 * configures nine identifiers (with case variants, an alias, duplicates, an unsupported language
 * and a region code); five tenants have their own settings. Every configured target gets an
 * explicit result and the original query is always returned.
 */
@QuarkusTest
@TestProfile(MultilingualApiTest.Expansion.class)
class MultilingualApiTest {

  static final String APP = "test-only-multilingual-app-key-12345";
  static final String NORDIC = "test-only-multilingual-nordic-key-123";
  static final String ASIA = "test-only-multilingual-asia-key-12345";
  static final String OFF = "test-only-multilingual-off-key-123456";
  static final String EMPTY = "test-only-multilingual-empty-key-12345";
  static final String QUERY = "I need a red winter jacket under 100 euros";

  public static class Expansion implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      var c = new HashMap<>(new ServiceFreeProfile().getConfigOverrides());
      c.put("qwenbridge.multilingual.enabled", "true");
      c.put("qwenbridge.multilingual.target-languages", "en,FA,sv,nb,nn,SE,ja,DK,fa");
      c.put("qwenbridge.multilingual.providers", "glossary");
      c.put("qwenbridge.multilingual.glossary-dir", "src/test/resources/multilingual/glossary");
      c.put(
          "qwenbridge.multilingual.tenants",
          "{\"nordic\":{\"enabled\":true,\"targetLanguages\":[\"sv\",\"nb\",\"nn\",\"de\"],"
              + "\"policy\":\"STRICT_ALL_TARGETS\"},"
              + "\"asia\":{\"enabled\":true,\"targetLanguages\":[\"ja\",\"ko\",\"zh-Hans\"]},"
              + "\"off\":{\"enabled\":false},"
              + "\"empty\":{\"enabled\":true,\"targetLanguages\":[]}}");
      c.put(
          "qwenbridge.security.api-principals",
          "["
              + principal(APP, "shopA")
              + ","
              + principal(NORDIC, "nordic")
              + ","
              + principal(ASIA, "asia")
              + ","
              + principal(OFF, "off")
              + ","
              + principal(EMPTY, "empty")
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
  void configurationIsResolvedPerTenantWithProviderCapabilities() {
    var app = as(APP).get("/api/v2/multilingual").then().statusCode(200).extract().jsonPath();
    assertThat(app.getString("scope")).isEqualTo("application");
    assertThat(app.getBoolean("enabled")).isTrue();
    assertThat(app.getString("policy")).isEqualTo("ALL_TARGETS");
    assertThat(app.getList("targets.configured"))
        .containsExactly("en", "FA", "sv", "nb", "nn", "SE", "ja", "DK", "fa");
    assertThat(app.getList("targets.resolution"))
        .containsExactly(
            "VALID",
            "NORMALIZED",
            "VALID",
            "VALID",
            "VALID",
            "DUPLICATE",
            "VALID",
            "INVALID",
            "DUPLICATE");
    assertThat(app.getString("targets[5].note"))
        .isEqualTo("compatibility alias SE -> sv; duplicate of sv (sv)");
    assertThat(app.getList("targets[1].providers")).containsExactly("glossary");
    assertThat(app.getList("targets[6].providers")).isEmpty();
    assertThat(app.getList("providers.id")).containsExactly("glossary");
    assertThat(app.getString("providers[0].status")).isEqualTo("READY");
    assertThat(app.<String>getList("providers[0].pairs"))
        .containsExactlyInAnyOrder("en>fa", "en>nb", "en>nn", "en>sv", "sv>en");
    assertThat(app.getInt("limits.maxConcurrentTranslations")).isEqualTo(16);

    var nordic = as(NORDIC).get("/api/v2/multilingual").then().statusCode(200).extract().jsonPath();
    assertThat(nordic.getString("scope")).isEqualTo("tenant");
    assertThat(nordic.getString("policy")).isEqualTo("STRICT_ALL_TARGETS");
    assertThat(nordic.getList("targets.configured")).containsExactly("sv", "nb", "nn", "de");
    var off = as(OFF).get("/api/v2/multilingual").then().statusCode(200).extract().jsonPath();
    assertThat(off.getBoolean("enabled")).isFalse();
  }

  @Test
  void oneQueryBecomesOneResultPerConfiguredLanguage() {
    var r = search(APP, QUERY);
    assertThat(r.getString("expansion.original")).isEqualTo(QUERY);
    // The detector labels this English query Swedish (statistical, no score); the provider
    // vocabulary establishes English, and the response says so.
    assertThat(r.getString("expansion.source.language")).isEqualTo("en");
    assertThat(r.getString("expansion.source.basis")).isEqualTo("VOCABULARY");
    assertThat(r.getString("expansion.explanations[0]")).contains("said sv");
    assertThat(r.getList("expansion.requestedLanguages"))
        .containsExactly("en", "FA", "sv", "nb", "nn", "ja", "DK");
    assertThat(r.getList("expansion.targets.status"))
        .containsExactly(
            "UNCHANGED",
            "SUCCESS",
            "SUCCESS",
            "SUCCESS",
            "SUCCESS",
            "UNSUPPORTED_LANGUAGE",
            "INVALID_LANGUAGE");
    assertThat(r.getList("expansion.targets.language"))
        .containsExactly("en", "fa", "sv", "nb", "nn", "ja", null);
    assertThat(r.getString("expansion.targets[1].text"))
        .isEqualTo("من نیاز دارم به یک ژاکت زمستانی قرمز زیر 100 یورو");
    assertThat(r.getString("expansion.targets[2].text"))
        .isEqualTo("jag behöver en röd vinterjacka under 100 euro");
    assertThat(r.getString("expansion.targets[2].provider")).isEqualTo("glossary");
    assertThat(r.getString("expansion.targets[2].preservation.status")).isEqualTo("PRESERVED");
    assertThat(r.getString("expansion.targets[3].preservation.status"))
        .isEqualTo("PARTIALLY_VERIFIED");
    assertThat(r.<Object>getList("expansion.targets.text").get(5)).isNull();
    assertThat(r.getList("expansion.unsupportedLanguages")).containsExactly("ja", "DK");
    assertThat(r.getList("expansion.successfulLanguages"))
        .containsExactly("en", "fa", "sv", "nb", "nn");
    assertThat(r.getString("expansion.status")).isEqualTo("PARTIAL");
    assertThat(r.getList("expansion.retrievalTexts")).hasSize(5);
    String ref = r.getString("expansion.originalRef");
    assertThat(r.getList("expansion.targets.originalRef")).allMatch(ref::equals);
  }

  @Test
  void tenantPoliciesAndLanguagesStayIsolated() {
    var nordic = search(NORDIC, QUERY);
    assertThat(nordic.getList("expansion.targets.language"))
        .containsExactly("sv", "nb", "nn", "de");
    assertThat(nordic.getList("expansion.targets.status"))
        .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "UNSUPPORTED_LANGUAGE");
    assertThat(nordic.getString("expansion.status")).isEqualTo("FAILED");
    assertThat(nordic.getList("expansion.retrievalTexts")).containsExactly(QUERY);

    var asia = search(ASIA, QUERY);
    assertThat(asia.getList("expansion.targets.status"))
        .containsExactly("UNSUPPORTED_LANGUAGE", "UNSUPPORTED_LANGUAGE", "UNSUPPORTED_LANGUAGE");
    assertThat(asia.getList("expansion.targets.language")).containsExactly("ja", "ko", "zh-Hans");
    assertThat(asia.<Object>getList("expansion.targets.text")).containsOnlyNulls();

    var empty = search(EMPTY, QUERY);
    assertThat(empty.getString("expansion.status")).isEqualTo("NOT_CONFIGURED");
    assertThat(empty.getList("expansion.targets")).isEmpty();

    String off =
        as(OFF)
            .body(Map.of("query", QUERY))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(off).doesNotContain("\"expansion\"");
  }

  @Test
  void technicalIdentifiersSurviveTranslation() {
    var r = search(NORDIC, "show errors from redis03-pt1 in the last 30 minutes");
    assertThat(r.getString("expansion.targets[0].status")).isEqualTo("SUCCESS");
    assertThat(r.getString("expansion.targets[0].text"))
        .isEqualTo("visa fel från redis03-pt1 de senaste 30 minuterna");
    assertThat(r.getBoolean("expansion.targets[0].identifiersPreserved")).isTrue();
  }

  @Test
  void theSseTerminalEventCarriesTheExpansion() {
    as(APP)
        .body(Map.of("query", QUERY))
        .post("/api/v2/search/stream")
        .then()
        .statusCode(200)
        .body(
            containsString("event:search.completed"),
            containsString("\"expansion\":{\"status\":\"PARTIAL\""),
            containsString("jag behöver en röd vinterjacka under 100 euro"));
    as(OFF)
        .body(Map.of("query", QUERY))
        .post("/api/v2/search/stream")
        .then()
        .statusCode(200)
        .body(not(containsString("\"expansion\"")));
  }

  @Test
  void concurrentTenantsDoNotInterfere() throws Exception {
    try (var pool = Executors.newFixedThreadPool(6)) {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        String key = i % 2 == 0 ? NORDIC : APP;
        results.add(
            pool.submit(
                () ->
                    (key.equals(NORDIC) ? "nordic=" : "app=")
                        + search(key, QUERY).getList("expansion.requestedLanguages")));
      }
      for (var r : results)
        assertThat(r.get()).isIn("nordic=[sv, nb, nn, de]", "app=[en, FA, sv, nb, nn, ja, DK]");
    }
  }

  /**
   * Server half of the SDK contract (QB-286): the real expansion object is written to {@code
   * target/contract/v2-search-expansion.json}; every field it contains must be present in the SDK
   * golden fixture, which the Java and TypeScript SDK tests parse.
   */
  @Test
  void everyExpansionFieldIsCoveredByTheSdkFixture() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var body =
        mapper.readTree(
            as(APP)
                .body(Map.of("query", QUERY))
                .post("/api/v2/search")
                .then()
                .statusCode(200)
                .extract()
                .asString());
    var actual = mapper.createObjectNode().set("expansion", body.get("expansion"));
    var out = java.nio.file.Path.of("target", "contract", "v2-search-expansion.json");
    java.nio.file.Files.createDirectories(out.getParent());
    java.nio.file.Files.writeString(
        out, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual));
    var fixture =
        java.nio.file.Path.of(
            "..",
            "qwenbridge-java-sdk",
            "src",
            "test",
            "resources",
            "contract",
            "v2-search-expansion.json");
    assertThat(fixture).as("SDK golden fixture; refresh it from %s", out).exists();
    var expected = mapper.readTree(java.nio.file.Files.readAllBytes(fixture));
    assertThat(fields(expected.get("expansion"))).containsAll(fields(actual.get("expansion")));
  }

  /** Field paths of an object, descending into objects and every element of arrays. */
  static java.util.Set<String> fields(com.fasterxml.jackson.databind.JsonNode node) {
    var out = new java.util.TreeSet<String>();
    collect(node, "", out);
    return out;
  }

  private static void collect(
      com.fasterxml.jackson.databind.JsonNode node, String path, java.util.Set<String> out) {
    if (node.isObject())
      node.properties()
          .forEach(
              e -> {
                out.add(path + "/" + e.getKey());
                collect(e.getValue(), path + "/" + e.getKey(), out);
              });
    else if (node.isArray()) node.forEach(child -> collect(child, path + "[]", out));
  }
}
