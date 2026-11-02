package io.qwenbridge.commerce;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.catalog.CatalogNormalizer;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.connectors.CommerceBackends;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-091: the full Commerce search API on a local synthetic catalog, with the in-process backend.
 * No Shopify, database or search service is involved: OpenSearch is mocked only to prove it is
 * never called. The language model is mocked (its output is the analysis under test elsewhere).
 * Documents are ingested through the Commerce pack's canonical catalog pipeline.
 */
@QuarkusTest
@TestProfile(SyntheticCatalogSearchTest.Memory.class)
class SyntheticCatalogSearchTest {
  static final String KEY_A = "test-only-synthetic-alice-123456";
  static final String KEY_B = "test-only-synthetic-bob-12345678";
  static final String QUERY = "black Nike shoes size 42 under 100 EUR not refurbished in stock";
  static final String MULTI = "black Nike shoes size 42 and a white backpack under 100 EUR";
  static final String V1 = CommerceContracts.SearchRequest.CONTRACT_V1;

  public static class Memory implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.commerce.backend",
          "memory",
          // No embedding service in this offline test: vector retrieval reports itself unavailable.
          "qwenbridge.embedding.enabled",
          "false",
          "qwenbridge.security.authentication-required",
          "true",
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + KEY_A
              + "\",\"subject\":\"alice\",\"tenant\":\"shopA\",\"roles\":[\"search\"]},"
              + "{\"key\":\""
              + KEY_B
              + "\",\"subject\":\"bob\",\"tenant\":\"shopB\",\"roles\":[\"search\"]}]");
    }
  }

  @InjectMock SearchAnalysisService analysis;
  @InjectMock OpenSearchClient opensearch;
  @InjectMock CommerceEvidenceStore evidence;
  @InjectMock CommerceAnalytics analytics;
  @Inject CommerceBackends backends;

  private static Map<String, Object> product(
      String title,
      String brand,
      String color,
      String size,
      String price,
      String currency,
      Boolean inStock,
      String condition) {
    var raw = new HashMap<String, Object>();
    raw.put("title", title);
    raw.put("brand", brand);
    raw.put("category", "Footwear");
    raw.put("color", color);
    raw.put("size", size);
    raw.put("price", price);
    raw.put("currency", currency);
    if (inStock != null) raw.put("inStock", inStock);
    if (condition != null) raw.put("condition", condition);
    return raw;
  }

  /** The synthetic catalog, keyed by document id; only s1 and s10 satisfy every constraint. */
  static final Map<String, Map<String, Object>> CATALOG = new java.util.LinkedHashMap<>();

  static {
    CATALOG.put(
        "s1", product("Nike running shoes", "Nike", "black", "42", "89.90", "EUR", true, "new"));
    CATALOG.put(
        "s2",
        product("Nike running shoes", "Nike", "black", "42", "95", "EUR", true, "refurbished"));
    CATALOG.put(
        "s3", product("Nike trail shoes", "Nike", "black", "42", "120", "EUR", true, "new"));
    CATALOG.put("s4", product("Nike court shoes", "Nike", "white", "42", "80", "EUR", true, "new"));
    CATALOG.put("s5", product("Adidas shoes", "Adidas", "black", "42", "70", "EUR", true, "new"));
    CATALOG.put(
        "s6", product("Nike walking shoes", "Nike", "black", "42", "60", "EUR", false, "new"));
    CATALOG.put("s7", product("Nike road shoes", "Nike", "black", "43", "75", "EUR", true, "new"));
    CATALOG.put("s8", product("Nike city shoes", "Nike", "black", "42", "85", "SEK", true, "new"));
    // Commerce v2 semantics: "not refurbished" requires a stated condition (CommerceCompiler).
    CATALOG.put("s9", product("Nike gym shoes", "Nike", "black", "42", "99", "EUR", true, null));
    CATALOG.put(
        "s10", product("Nike black sneakers", "NIKE", "Noir", "42", "49", "EUR", true, "new"));
    // A different product type for the multi-product query; it fails the single-product query.
    CATALOG.put(
        "s11", product("Osprey day backpack", "Osprey", "white", "OS", "60", "EUR", true, "new"));
  }

  @BeforeEach
  void setup() {
    var normalizer = new CatalogNormalizer();
    CATALOG.forEach((id, raw) -> index("shopA", id, normalizer, raw));
    // Another tenant's identical, cheaper product must never appear for shopA.
    index("shopB", "b1", normalizer, CATALOG.get("s1"));
    var constraints =
        List.of(
            c("brand", Operator.EQ, "Nike", "Nike"),
            c("color", Operator.EQ, "black", "black"),
            c("size", Operator.EQ, "42", "42"),
            c("price", Operator.LT, "100", "under 100 EUR"),
            c("currency", Operator.EQ, "EUR", "EUR"),
            c("condition", Operator.NE, "refurbished", "not refurbished"),
            c("inStock", Operator.EQ, "true", "in stock"));
    when(analysis.analyze(anyString())).thenReturn(model(constraints, QUERY));
    when(analysis.analyze(MULTI))
        .thenReturn(
            model(
                List.of(
                    c(MULTI, "color", Operator.EQ, "black", "black"),
                    c(MULTI, "brand", Operator.EQ, "Nike", "Nike"),
                    c(MULTI, "size", Operator.EQ, "42", "42"),
                    c(MULTI, "color", Operator.EQ, "white", "white"),
                    c(MULTI, "price", Operator.LT, "100", "under 100 EUR"),
                    c(MULTI, "currency", Operator.EQ, "EUR", "EUR")),
                MULTI));
    when(evidence.recordQuery(
            anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(i -> attribution(i.getArgument(0), i.getArgument(1), "query"));
    when(evidence.record(anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(i -> attribution(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
  }

  private void index(
      String tenant, String id, CatalogNormalizer normalizer, Map<String, Object> raw) {
    var product = normalizer.normalize("http-json", id, raw);
    backends.upsert(
        tenant, id, normalizer.document(product, product.variants().getFirst(), tenant));
  }

  @Test
  void returnsExactlyTheProductsThatSatisfyEveryConstraintWithinTheTenant() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("results.totalHits", equalTo(2))
        .body("results.hits.id", containsInAnyOrder("s1", "s10"))
        .body("results.hits.document.tenantId", everyItem(equalTo("shopA")))
        .body("results.hits.document.color", everyItem(equalTo("black")));
    given()
        .header("Authorization", "Bearer " + KEY_B)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("results.hits.id", org.hamcrest.Matchers.contains("b1"));
    verifyNoInteractions(opensearch);
  }

  @Test
  void pagesAreStableAndCoverTheResultSetOnce() {
    var first =
        given()
            .header("Authorization", "Bearer " + KEY_A)
            .contentType("application/json")
            .body(Map.of("query", QUERY, "limit", 1))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .body("results.hits", hasSize(1))
            .body("nextCursor", notNullValue())
            .extract();
    String cursor = first.path("nextCursor");
    var second =
        given()
            .header("Authorization", "Bearer " + KEY_A)
            .contentType("application/json")
            .body(Map.of("query", QUERY, "limit", 1, "cursor", cursor))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .body("results.hits", hasSize(1))
            .body("nextCursor", nullValue())
            .extract();
    var seen = new ArrayList<String>();
    seen.add(first.path("results.hits[0].id"));
    seen.add(second.path("results.hits[0].id"));
    org.assertj.core.api.Assertions.assertThat(seen).containsExactlyInAnyOrder("s1", "s10");
  }

  @Test
  void connectorsReportTheirLifecycleState() {
    // A connector is DISABLED exactly when it is not configured: the in-process backend always is,
    // the optional OpenSearch backend is in this profile too (QB-287: no other built-in backend).
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .get("/api/v2/connectors")
        .then()
        .statusCode(200)
        .body("id", containsInAnyOrder("memory", "opensearch"))
        .body("find { it.id == 'memory' }.state", equalTo("READY"))
        .body("find { it.id == 'memory' }.circuit", equalTo("CLOSED"))
        .body("configuration", everyItem(equalTo("connector-config/1")));
  }

  @Test
  void backendsComeFromTheRegistryAndUnsupportedModesAreRefused() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "backend", "memory"))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("results.totalHits", equalTo(2));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "backend", "nosuch"))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "backend", "Not A Backend"))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "mode", "VECTOR"))
        .post("/api/v2/search")
        .then()
        .statusCode(422);
  }

  @Test
  void versionedContractPlansRetrievalReportsItAndUsesNeutralExclusions() {
    var body =
        given()
            .header("Authorization", "Bearer " + KEY_A)
            .contentType("application/json")
            .body(Map.of("query", QUERY, "contract", V1))
            .post("/api/v2/search")
            .then()
            .statusCode(200)
            .body("status", equalTo("OK"))
            .body("contract", equalTo(V1))
            .body("retrieval.executed", equalTo("KEYWORD_ONLY"))
            .body("retrieval.providers", hasItem("memory"))
            .body("retrieval.removedByValidation", equalTo(0))
            .body("retrieval.rankingPolicy", equalTo("qwenbridge-baseline@1.0"))
            .body("retrieval.reranking", equalTo("none"))
            .body("results.hits[0].metadata.position", equalTo(1))
            .body("results.hits[0].metadata.scoreComponents.kind", hasItem("RETRIEVAL"))
            .body("results.hits[0].metadata.retrievalScore.kind", notNullValue())
            .body("interpretation.constraints.status", everyItem(equalTo("EXECUTABLE")))
            .body("interpretation.constraints.field", hasItems("brand", "price", "condition"))
            // s9 states no condition: "not refurbished" keeps it under the neutral semantics,
            // while apiVersion 2 excludes it (see the first test).
            .body("results.hits.id", containsInAnyOrder("s1", "s9", "s10"))
            .extract()
            .jsonPath();
    var reasons = body.getList("retrieval.reasons", String.class);
    boolean semanticWanted = reasons.contains("SEMANTIC_UNAVAILABLE");
    // Without an embedding service a semantic need is reported as degraded, never hidden.
    org.assertj.core.api.Assertions.assertThat(body.getBoolean("retrieval.degraded"))
        .isEqualTo(semanticWanted);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("$", not(hasKey("contract")))
        .body("$", not(hasKey("retrieval")))
        .body("$", not(hasKey("interpretation")))
        .body("$", not(hasKey("subqueries")));
  }

  @Test
  void versionedContractRefusesClientStrategiesAndUnknownVersions() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "contract", V1, "mode", "HYBRID"))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "contract", "commerce-search/9.9"))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "contract", V1, "limit", 40))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
  }

  @Test
  void multiProductQueriesAreAnsweredPerSubquery() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", MULTI, "contract", V1))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("results.hits", empty())
        .body("subqueries", hasSize(2))
        .body("subqueries[0].objective", equalTo("black Nike shoes size 42"))
        .body("subqueries[0].constraints.field", containsInAnyOrder("color", "brand", "size"))
        .body("subqueries[0].results.hits.id", hasItem("s1"))
        .body("subqueries[0].results.hits.document.color", everyItem(equalTo("black")))
        .body("subqueries[0].results.hits.document.size", everyItem(equalTo("42")))
        .body("subqueries[1].constraints.field", containsInAnyOrder("color", "price", "currency"))
        .body("subqueries[1].results.hits.id", hasItem("s11"))
        .body("subqueries[1].results.hits.document.color", everyItem(equalTo("white")));
  }

  private static Attribution attribution(String tenant, String source, String claim) {
    return new Attribution(
        "a".repeat(64), tenant, "", source, Instant.now(), Instant.now(), claim, List.of(), null);
  }

  private static Constraint c(String field, Operator op, String value, String quote) {
    return c(QUERY, field, op, value, quote);
  }

  private static Constraint c(String query, String field, Operator op, String value, String quote) {
    int start = query.indexOf(quote);
    return new Constraint(
        field, op, value, new Evidence("original", start, start + quote.length(), quote));
  }

  private static SearchAnalysis model(List<Constraint> filters, String rewrite) {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(.95)
        .semanticValidated(true)
        .semanticScore(.95)
        .decisionConfidence(.9)
        .rewrites(List.of(rewrite))
        .constraints(filters)
        .ambiguities(List.of())
        .modelThreatDecision("ALLOW")
        .build();
  }
}
