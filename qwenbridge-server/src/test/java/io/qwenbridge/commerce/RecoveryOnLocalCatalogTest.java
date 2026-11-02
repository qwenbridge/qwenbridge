package io.qwenbridge.commerce;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.catalog.CatalogNormalizer;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.connectors.CommerceBackends;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.platform.PlatformStore;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-131: zero-result recovery end to end on the real in-process backend and the synthetic catalog,
 * with no search service or database. Search history and evidence are kept in memory (their Redis
 * stores are replaced by maps); the backend, the policy, the offers and their acceptance are the
 * production code.
 */
@QuarkusTest
@TestProfile(SyntheticCatalogSearchTest.Memory.class)
class RecoveryOnLocalCatalogTest {
  static final String KEY = SyntheticCatalogSearchTest.KEY_A;
  static final String QUERY = "black Nike shoes size 42 under 45 EUR not refurbished";

  @InjectMock SearchAnalysisService analysis;
  @InjectMock OpenSearchClient opensearch;
  @InjectMock CommerceEvidenceStore evidence;
  @InjectMock CommerceAnalytics analytics;
  @Inject CommerceBackends backends;
  @Inject PlatformStore store;

  final Map<String, SearchResponse> history = new ConcurrentHashMap<>();

  static Constraint c(String field, Operator op, String value, String quote) {
    int start = QUERY.indexOf(quote);
    return new Constraint(
        field, op, value, new Evidence("original", start, start + quote.length(), quote));
  }

  @BeforeEach
  void setup() {
    var normalizer = new CatalogNormalizer();
    SyntheticCatalogSearchTest.CATALOG.forEach(
        (id, raw) -> {
          var product = normalizer.normalize("http-json", id, raw);
          backends.upsert(
              "shopA", id, normalizer.document(product, product.variants().getFirst(), "shopA"));
        });
    when(analysis.analyze(anyString()))
        .thenReturn(
            SearchAnalysis.builder()
                .language("en")
                .intent(IntentType.PRODUCT_SEARCH)
                .intentConfidence(.95)
                .semanticValidated(true)
                .semanticScore(.95)
                .decisionConfidence(.9)
                .rewrites(List.of(QUERY))
                .constraints(
                    List.of(
                        c("brand", Operator.EQ, "Nike", "Nike"),
                        c("color", Operator.EQ, "black", "black"),
                        c("size", Operator.EQ, "42", "42"),
                        c("price", Operator.LT, "45", "under 45 EUR"),
                        c("currency", Operator.EQ, "EUR", "EUR"),
                        c("condition", Operator.NE, "refurbished", "not refurbished")))
                .ambiguities(List.of())
                .modelThreatDecision("ALLOW")
                .build());
    var counter = new java.util.concurrent.atomic.AtomicInteger();
    when(evidence.recordQuery(
            anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(i -> attribution(i.getArgument(0), i.getArgument(1), "q", counter));
    when(evidence.record(anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(
            i -> attribution(i.getArgument(0), i.getArgument(1), i.getArgument(2), counter));
    doAnswer(
            i -> {
              SearchResponse r = i.getArgument(1);
              history.put(r.requestId(), r);
              return null;
            })
        .when(analytics)
        .search(any(), any());
    when(analytics.previous(any(), anyString()))
        .thenAnswer(
            i -> {
              var r = history.get((String) i.getArgument(1));
              if (r == null) throw new jakarta.ws.rs.NotFoundException();
              return r;
            });
  }

  static Attribution attribution(
      String tenant, String source, String claim, java.util.concurrent.atomic.AtomicInteger n) {
    return new Attribution(
        String.format("%064d", n.incrementAndGet()),
        tenant,
        "",
        source,
        Instant.now(),
        Instant.now(),
        claim,
        List.of(),
        null);
  }

  private String search() {
    return given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("results.totalHits", equalTo(0))
        .extract()
        .path("requestId");
  }

  @Test
  void zeroResultsAreExplainedOfferedAndOnlyAcceptedOffersBroadenTheQuery() {
    String id = search();
    var recovery =
        given()
            .header("Authorization", "Bearer " + KEY)
            .get("/api/v2/search/" + id + "/recovery")
            .then()
            .statusCode(200)
            .body("outcome", equalTo("ZERO_RESULTS"))
            .body("policy", equalTo("commerce-relaxation@1.0"))
            .body(
                "reasons", hasItems("ZERO_RESULTS", "HARD_REQUIRES_CONSENT", "RELAXATION_OFFERED"))
            .body("proposals.changedField", containsInAnyOrder("color", "size", "price"))
            .body("proposals.requiresConsent", everyItem(equalTo(true)))
            .body(
                "kept.findAll { it.field == 'condition' }.reason", hasItem("EXCLUSION_NOT_RELAXED"))
            .body("kept.findAll { it.field == 'currency' }.reason", hasItem("PROTECTED_FIELD"))
            .body("kept.findAll { it.field == 'brand' }.reason", hasItem("NO_PERMITTED_RELAXATION"))
            .extract()
            .jsonPath();
    String price = recovery.getString("proposals.find { it.changedField == 'price' }.id");
    org.assertj.core.api.Assertions.assertThat(
            recovery.getString("proposals.find { it.changedField == 'price' }.description"))
        .isEqualTo(
            "Raise the maximum budget from 45 to 49.50 EUR; preserve every other condition.");
    // Nothing is broadened by proposing: the same search still finds nothing.
    search();
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .post("/api/v2/relaxations/" + price + "/accept")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("results.hits.id", org.hamcrest.Matchers.contains("s10"))
        .body(
            "queryIR.constraints.findAll { it.field == 'price' }.value",
            org.hamcrest.Matchers.contains("49.50"))
        .body(
            "queryIR.constraints.findAll { it.field == 'condition' }.operator",
            org.hamcrest.Matchers.contains("NE"))
        .body(
            "queryIR.constraints.findAll { it.field == 'currency' }.value",
            org.hamcrest.Matchers.contains("EUR"));
  }

  @Test
  void aTamperedOfferIsRefusedBeforeItRuns() {
    String id = search();
    String color =
        given()
            .header("Authorization", "Bearer " + KEY)
            .get("/api/v2/search/" + id + "/relaxations")
            .then()
            .statusCode(200)
            .extract()
            .path("find { it.changedField == 'color' }.id");
    // Someone with store access drops the exclusion from the held alternative as well.
    store.update(
        "shopA",
        "relaxations",
        color,
        ZeroResultRecovery.Offer.class,
        Duration.ofMinutes(5),
        o ->
            new ZeroResultRecovery.Offer(
                o.id(),
                o.tenant(),
                o.subject(),
                o.parentRequestId(),
                o.backend(),
                o.description(),
                o.field(),
                o.constraints().stream().filter(x -> !x.field().equals("condition")).toList(),
                o.parent(),
                o.expiresAt(),
                o.status(),
                null,
                null,
                null,
                o.kind()));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .post("/api/v2/relaxations/" + color + "/accept")
        .then()
        .statusCode(409)
        .body("message", containsString("ALTERNATIVE_REMOVES_EXCLUSION"));
  }
}
