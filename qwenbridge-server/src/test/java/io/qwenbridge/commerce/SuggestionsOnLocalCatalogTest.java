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
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-132 – QB-138 on the real in-process backend and the synthetic catalog: typed grounded
 * suggestions, refinement that keeps every other condition, typed negative feedback and targeted
 * clarification questions. Search history and evidence are kept in memory; the model is mocked.
 */
@QuarkusTest
@TestProfile(SyntheticCatalogSearchTest.Memory.class)
class SuggestionsOnLocalCatalogTest {
  static final String KEY = SyntheticCatalogSearchTest.KEY_A;
  static final String QUERY = "Nike shoes under 100 EUR";
  static final String CONFLICT = "black or white Nike shoes";
  static final String NO_CURRENCY = "Nike shoes under 100";

  @InjectMock SearchAnalysisService analysis;
  @InjectMock OpenSearchClient opensearch;
  @InjectMock CommerceEvidenceStore evidence;
  @InjectMock CommerceAnalytics analytics;
  @Inject CommerceBackends backends;
  @InjectMock io.qwenbridge.ai.service.AIService ai;

  final Map<String, SearchResponse> history = new ConcurrentHashMap<>();

  static Constraint c(String query, String field, Operator op, String value, String quote) {
    int start = query.indexOf(quote);
    return new Constraint(
        field, op, value, new Evidence("original", start, start + quote.length(), quote));
  }

  static SearchAnalysis model(String query, List<Constraint> constraints) {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(.95)
        .semanticValidated(true)
        .semanticScore(.95)
        .decisionConfidence(.9)
        .rewrites(List.of(query))
        .constraints(constraints)
        .ambiguities(List.of())
        .modelThreatDecision("ALLOW")
        .build();
  }

  /**
   * Deterministic stand-in for the model translation: the detector may label this English query as
   * another language, and a faithful translation to English returns it unchanged. Without this the
   * test depended on a live local model.
   */
  static void faithfulTranslation(io.qwenbridge.ai.service.AIService ai) {
    when(ai.chat(org.mockito.ArgumentMatchers.any(io.qwenbridge.ai.contract.ChatRequest.class)))
        .thenAnswer(
            call -> {
              String prompt = call.<io.qwenbridge.ai.contract.ChatRequest>getArgument(0).prompt();
              String text = prompt.substring(prompt.lastIndexOf('\n') + 1);
              return new io.qwenbridge.ai.contract.ChatResponse("{\"text\":" + text + "}");
            });
  }

  @BeforeEach
  void setup() {
    faithfulTranslation(ai);
    var normalizer = new CatalogNormalizer();
    SyntheticCatalogSearchTest.CATALOG.forEach(
        (id, raw) -> {
          var product = normalizer.normalize("http-json", id, raw);
          backends.upsert(
              "shopA", id, normalizer.document(product, product.variants().getFirst(), "shopA"));
        });
    when(analysis.analyze(QUERY))
        .thenReturn(
            model(
                QUERY,
                List.of(
                    c(QUERY, "brand", Operator.EQ, "Nike", "Nike"),
                    c(QUERY, "price", Operator.LT, "100", "under 100 EUR"),
                    c(QUERY, "currency", Operator.EQ, "EUR", "EUR"))));
    when(analysis.analyze(CONFLICT))
        .thenReturn(
            model(
                CONFLICT,
                List.of(
                    c(CONFLICT, "color", Operator.EQ, "black", "black"),
                    c(CONFLICT, "color", Operator.EQ, "white", "white"),
                    c(CONFLICT, "brand", Operator.EQ, "Nike", "Nike"))));
    when(analysis.analyze(NO_CURRENCY))
        .thenReturn(
            model(
                NO_CURRENCY,
                List.of(
                    c(NO_CURRENCY, "brand", Operator.EQ, "Nike", "Nike"),
                    c(NO_CURRENCY, "price", Operator.LT, "100", "under 100"))));
    var counter = new AtomicInteger();
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

  static Attribution attribution(String tenant, String source, String claim, AtomicInteger n) {
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
        .body("results.hits.id", containsInAnyOrder("s1", "s2", "s4", "s6", "s7", "s9", "s10"))
        .extract()
        .path("requestId");
  }

  private io.restassured.path.json.JsonPath suggestions(String id) {
    return given()
        .header("Authorization", "Bearer " + KEY)
        .get("/api/v2/search/" + id + "/suggestions")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath();
  }

  @Test
  void suggestionsAreTypedGroundedAndDeterministic() {
    String id = search();
    var first = suggestions(id);
    org.assertj.core.api.Assertions.assertThat(first.getString("version"))
        .isEqualTo("suggestions/1.0");
    org.assertj.core.api.Assertions.assertThat(first.getList("queries")).isNotNull();
    // Seven results; "condition: new" (5 of 7) splits them most evenly. Brand is already fixed;
    // category is the same for all results, so neither is suggested.
    org.assertj.core.api.Assertions.assertThat(first.getString("items[0].text"))
        .isEqualTo("condition: new");
    org.assertj.core.api.Assertions.assertThat(first.getInt("items[0].support")).isEqualTo(5);
    org.assertj.core.api.Assertions.assertThat(first.getList("items.text", String.class))
        .containsExactly(
            "condition: new",
            "color: black",
            "color: white",
            "size: 42",
            "size: 43",
            "condition: refurbished");
    org.assertj.core.api.Assertions.assertThat(first.getList("items.grounded", Boolean.class))
        .containsOnly(true);
    org.assertj.core.api.Assertions.assertThat(first.getList("items.id"))
        .isEqualTo(suggestions(id).getList("items.id"));
  }

  @Test
  void applyingASuggestionNarrowsAndKeepsEveryOtherCondition() {
    String id = search();
    String white = suggestions(id).getString("items.find { it.text == 'color: white' }.id");
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .post("/api/v2/search/" + id + "/suggestions/" + white + "/apply")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("results.hits.id", org.hamcrest.Matchers.contains("s4"))
        .body(
            "queryIR.constraints.field", containsInAnyOrder("brand", "price", "currency", "color"))
        .body("queryIR.constraints.find { it.field == 'price' }.value", equalTo("100"))
        .body("queryIR.constraints.find { it.field == 'color' }.value", equalTo("white"));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .post("/api/v2/search/" + id + "/suggestions/unknown/apply")
        .then()
        .statusCode(404);
  }

  @Test
  void negativeFeedbackIsATypedUpdateAppliedToTheFollowUp() {
    String id = search();
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("kind", "EXCLUDE_VALUE", "field", "color", "value", "black"))
        .post("/api/v2/search/" + id + "/feedback")
        .then()
        .statusCode(200)
        .body("update.constraints[0].operator", equalTo("NE"))
        .body("update.constraints[0].field", equalTo("color"))
        .body("result.results.hits.id", org.hamcrest.Matchers.contains("s4"));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("kind", "NOT_RELEVANT", "documentId", "s1"))
        .post("/api/v2/search/" + id + "/feedback")
        .then()
        .statusCode(200)
        .body("update.suppressed", org.hamcrest.Matchers.contains("s1"))
        .body("update.constraints", empty())
        .body("result.results.hits.id", not(hasItem("s1")))
        .body("result.results.hits", hasSize(6));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("kind", "TOO_EXPENSIVE", "documentId", "s9", "field", "price"))
        .post("/api/v2/search/" + id + "/feedback")
        .then()
        .statusCode(200)
        .body("update.constraints", empty())
        .body("update.preferences[0].operator", equalTo("LT"))
        .body("update.preferences[0].value", equalTo("99"))
        .body("result.results.hits.id", not(hasItem("s9")));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("kind", "NOT_RELEVANT", "documentId", "not-a-result"))
        .post("/api/v2/search/" + id + "/feedback")
        .then()
        .statusCode(400);
  }

  @Test
  void clarificationQuestionsAreTargeted() {
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("query", CONFLICT, "contract", CommerceContracts.SearchRequest.CONTRACT_V1))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("CLARIFICATION_REQUIRED"))
        .body("clarifications.find { it.options.size() > 0 }.kind", equalTo("CONFLICT"))
        .body("clarifications.find { it.options.size() > 0 }.field", equalTo("color"))
        .body(
            "clarifications.find { it.options.size() > 0 }.options",
            containsInAnyOrder("black", "white"))
        .body(
            "clarifications.find { it.options.size() > 0 }.question",
            equalTo("You asked for more than one color: black or white. Which one do you want?"));
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("query", NO_CURRENCY, "contract", CommerceContracts.SearchRequest.CONTRACT_V1))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("CLARIFICATION_REQUIRED"))
        .body("clarifications.kind", hasItem("MISSING_QUALIFIER"))
        .body("clarifications.question", hasItem("Which currency is the price in?"));
    // apiVersion 2 keeps its string questions and has no typed field.
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("query", CONFLICT))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("clarificationQuestions", hasItem("Conflicting equality: color"))
        .body("$", not(hasKey("clarifications")));
  }
}
