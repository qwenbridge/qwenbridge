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
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CommerceApiTest.Keys.class)
class CommerceApiTest {
  static final String KEY_A = "test-only-alice-credential-123456";
  static final String KEY_B = "test-only-bob-credential-12345678";
  static final String QUERY = "black Nike shoes size 42 under 100 EUR not refurbished in stock";

  public static class Keys implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
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
  @InjectMock OpenSearchClient backend;
  @InjectMock CommerceEvidenceStore evidence;
  @InjectMock CommerceAnalytics analytics;

  @BeforeEach
  void setup() {
    var constraints =
        List.of(
            c("brand", Operator.EQ, "Nike", "Nike"),
            c("color", Operator.EQ, "black", "black"),
            c("size", Operator.EQ, "42", "42"),
            c("price", Operator.LT, "100", "under 100 EUR"),
            c("currency", Operator.EQ, "EUR", "EUR"),
            c("condition", Operator.NE, "refurbished", "not refurbished"),
            c("inStock", Operator.EQ, "true", "in stock"));
    when(analysis.analyze(anyString())).thenReturn(model(constraints, List.of()));
    when(backend.search(anyString(), anyMap())).thenReturn(hits("shopA", 99));
    when(evidence.recordQuery(
            anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(
            invocation ->
                new Attribution(
                    "a".repeat(64),
                    invocation.getArgument(0),
                    "",
                    invocation.getArgument(1),
                    Instant.now(),
                    Instant.now(),
                    "Search constraints",
                    invocation.getArgument(3),
                    null));
    when(evidence.record(anyString(), anyString(), anyString(), anyList(), isNull()))
        .thenAnswer(
            invocation ->
                new Attribution(
                    "a".repeat(64),
                    invocation.getArgument(0),
                    "",
                    invocation.getArgument(1),
                    Instant.now(),
                    Instant.now(),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    null));
    when(evidence.record(anyString(), anyString(), anyString(), anyList(), anyString()))
        .thenAnswer(
            invocation ->
                new Attribution(
                    "b".repeat(64),
                    invocation.getArgument(0),
                    "",
                    invocation.getArgument(1),
                    Instant.now(),
                    Instant.now(),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4)));
  }

  @Test
  void publicApiCompilesEveryFilterAndBindsTenantFromVerifiedCredential() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .header("X-Tenant-Id", "shopB")
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("apiVersion", equalTo(2))
        .body("status", equalTo("OK"))
        .body("queryIR.tenant", equalTo("shopA"))
        .body("queryIR.constraints.size()", equalTo(7))
        .body("results.hits.id", org.hamcrest.Matchers.contains("good"))
        .body("confidence.calibrated", equalTo(false));
    verify(backend)
        .search(
            anyString(),
            argThat(
                payload ->
                    payload.toString().contains("tenantId=shopA")
                        && new com.fasterxml.jackson.databind.ObjectMapper()
                            .valueToTree(payload).findValues("lt").stream()
                                .anyMatch(n -> n.asDouble() == 100)
                        && payload.toString().contains("must_not")));
  }

  @Test
  void authenticationAndAdministrativePermissionsAreEnforced() {
    given()
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(401);
    given()
        .header("Authorization", "Bearer wrong")
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(401);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .get("/api/v2/analytics")
        .then()
        .statusCode(403);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(CommerceSchemaRegistry.defaults())
        .post("/api/v2/schemas")
        .then()
        .statusCode(403);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(409);
    verifyNoInteractions(analysis, backend);
  }

  @Test
  void modelAmbiguityReturnsQuestionsWithoutExecutingSearch() {
    when(analysis.analyze(anyString()))
        .thenReturn(model(List.of(), List.of("Which size do you want?")));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("CLARIFICATION_REQUIRED"))
        .body("clarificationQuestions", hasItem("Which size do you want?"));
    verifyNoInteractions(backend);
  }

  @Test
  void invalidConstraintsAreNotSilentlyDropped() {
    when(analysis.analyze(anyString()))
        .thenReturn(model(List.of(c("unknownField", Operator.EQ, "Nike", "Nike")), List.of()));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("CLARIFICATION_REQUIRED"))
        .body("results.hits", empty());
    verifyNoInteractions(backend);
  }

  @Test
  void modelTimeoutFailsClosedBeforeBackendExecution() {
    when(analysis.analyze(anyString())).thenReturn(SearchAnalysis.fallback(QUERY));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200)
        .body("status", equalTo("REVIEW"))
        .body("results.hits", empty());
    verifyNoInteractions(backend);
  }

  @Test
  void backendCannotLeakAnotherTenantOrOutOfBudgetProduct() {
    when(backend.search(anyString(), anyMap())).thenReturn(hits("shopB", 99));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(500);
    when(backend.search(anyString(), anyMap())).thenReturn(hits("shopA", 100));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(500);
  }

  @Test
  void v2SseCarriesTypedTerminalResponseFromTheSameExecutionPath() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search/stream")
        .then()
        .statusCode(200)
        .body(
            containsString("event:search.started"),
            containsString("event:search.completed"),
            containsString("\"apiVersion\":2"));
  }

  @Test
  void addingLowerPriceBoundRetainsUpperBudgetAndPreviousNegativeConditions() {
    var priorPlan =
        CommerceCompiler.validate(
            CommerceSchemaRegistry.defaults(),
            "shopA",
            List.of(
                c("price", Operator.LT, "100", "under 100 EUR"),
                c("currency", Operator.EQ, "EUR", "EUR"),
                c("condition", Operator.NE, "refurbished", "not refurbished")));
    var prior =
        new SearchResponse(
            2,
            "previous",
            "OK",
            null,
            "SEARCH",
            priorPlan,
            List.of(),
            io.qwenbridge.execution.provider.model.SearchResultSet.empty(),
            null,
            List.of(
                new Attribution(
                    "a".repeat(64),
                    "shopA",
                    "previous",
                    "urn:query",
                    Instant.now(),
                    Instant.now(),
                    "Original constraints",
                    List.of(),
                    null)),
            List.of(),
            "none",
            0);
    when(analytics.previous(any(), eq("previous"))).thenReturn(prior);
    String query = "shoes over 50 EUR";
    var span = new Evidence("original", 6, query.length(), "over 50 EUR");
    when(analysis.analyze(query))
        .thenReturn(
            model(
                List.of(
                    new Constraint("price", Operator.GT, "50", span),
                    new Constraint("currency", Operator.EQ, "EUR", span)),
                List.of()));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", query))
        .post("/api/v2/search/previous/refine")
        .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("queryIR.constraints.findAll { it.field == 'price' }.operator", hasItems("LT", "GT"))
        .body("queryIR.constraints.findAll { it.field == 'condition' }.operator", hasItem("NE"));
  }

  private static Constraint c(String field, Operator op, String value, String quote) {
    int start = QUERY.indexOf(quote);
    return new Constraint(
        field, op, value, new Evidence("original", start, start + quote.length(), quote));
  }

  private static SearchAnalysis model(List<Constraint> filters, List<String> questions) {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(.95)
        .semanticValidated(true)
        .semanticScore(.95)
        .decisionConfidence(.9)
        .rewrites(List.of(QUERY))
        .constraints(filters)
        .ambiguities(questions)
        .modelThreatDecision("ALLOW")
        .build();
  }

  private static Map<String, Object> hits(String tenant, int price) {
    return Map.of(
        "took",
        1,
        "hits",
        Map.of(
            "total",
            Map.of("value", 1),
            "hits",
            List.of(
                Map.of(
                    "_id",
                    "good",
                    "_score",
                    1,
                    "_source",
                    Map.of(
                        "tenantId",
                        tenant,
                        "title",
                        "Nike shoes",
                        "brand",
                        "Nike",
                        "color",
                        "black",
                        "size",
                        "42",
                        "price",
                        price,
                        "currency",
                        "EUR",
                        "condition",
                        "new",
                        "inStock",
                        true)))));
  }

  // ---- QB-179 – QB-185: v2 API contract ----

  static final String TRACE = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  static final java.nio.file.Path SDK_CONTRACT =
      java.nio.file.Path.of("..", "qwenbridge-java-sdk", "src", "test", "resources", "contract");

  /** Every field name (nested ones as a/b) of the given JSON objects. */
  static java.util.Set<String> fieldNames(List<com.fasterxml.jackson.databind.JsonNode> nodes) {
    var names = new java.util.TreeSet<String>();
    for (var node : nodes)
      node.fieldNames()
          .forEachRemaining(
              f -> {
                names.add(f);
                if (!f.equals("response") && node.get(f).isObject())
                  node.get(f).fieldNames().forEachRemaining(g -> names.add(f + "/" + g));
              });
    return names;
  }

  /**
   * QB-185: a field the SDK fixtures do not contain forces the fixtures (and SDK tests) to move.
   */
  static void coveredBySdkFixtures(List<com.fasterxml.jackson.databind.JsonNode> serverEvents)
      throws Exception {
    var fixture = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
    for (String name : List.of("v2-search-stream-completed.sse", "v2-search-stream-failed.sse"))
      fixture.addAll(events(java.nio.file.Files.readString(SDK_CONTRACT.resolve(name))));
    org.assertj.core.api.Assertions.assertThat(fieldNames(fixture))
        .containsAll(fieldNames(serverEvents));
  }

  /** The JSON payloads of an SSE body, in order. */
  static List<com.fasterxml.jackson.databind.JsonNode> events(String body) throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var out = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
    for (String line : body.split("\\R"))
      if (line.startsWith("data:")) out.add(mapper.readTree(line.substring(5).strip()));
    return out;
  }

  @Test
  void v2StreamsAreSequencedCorrelatedAndEndWithExactlyOneTerminalEvent() throws Exception {
    var response =
        given()
            .header("Authorization", "Bearer " + KEY_A)
            .header("traceparent", TRACE)
            .contentType("application/json")
            .body(Map.of("query", QUERY, "requestId", "contract-stream-1"))
            .post("/api/v2/search/stream");
    response.then().statusCode(200).header("X-Trace-Id", TRACE.substring(3, 35));
    var events = events(response.asString());
    org.assertj.core.api.Assertions.assertThat(events)
        .extracting(e -> e.get("kind").asText())
        .containsExactly("search.started", "search.completed");
    for (int i = 0; i < events.size(); i++) {
      var e = events.get(i);
      org.assertj.core.api.Assertions.assertThat(e.get("version").asInt()).isEqualTo(2);
      org.assertj.core.api.Assertions.assertThat(e.get("sequence").asLong()).isEqualTo(i + 1);
      org.assertj.core.api.Assertions.assertThat(e.get("terminal").asBoolean())
          .isEqualTo(i == events.size() - 1);
      org.assertj.core.api.Assertions.assertThat(e.get("requestId").asText())
          .isEqualTo("contract-stream-1");
      org.assertj.core.api.Assertions.assertThat(e.get("traceId").asText())
          .isEqualTo(TRACE.substring(3, 35));
      org.assertj.core.api.Assertions.assertThat(e.has("error")).isFalse();
    }
    coveredBySdkFixtures(events);
  }

  @Test
  void aFailedStreamCarriesATypedErrorWithoutInternals() throws Exception {
    var events =
        events(
            given()
                .header("Authorization", "Bearer " + KEY_A)
                .contentType("application/json")
                .body(Map.of("query", QUERY, "backend", "nosuchbackend"))
                .post("/api/v2/search/stream")
                .then()
                .statusCode(200)
                .extract()
                .asString());
    var last = events.get(events.size() - 1);
    org.assertj.core.api.Assertions.assertThat(last.get("kind").asText())
        .isEqualTo("search.failed");
    org.assertj.core.api.Assertions.assertThat(last.get("terminal").asBoolean()).isTrue();
    org.assertj.core.api.Assertions.assertThat(last.get("message").asText())
        .isEqualTo("Search could not complete");
    var error = last.get("error");
    org.assertj.core.api.Assertions.assertThat(error.get("code").asText()).isEqualTo("BAD_REQUEST");
    org.assertj.core.api.Assertions.assertThat(error.get("category").asText())
        .isEqualTo("VALIDATION");
    org.assertj.core.api.Assertions.assertThat(error.get("retryable").asBoolean()).isFalse();
    org.assertj.core.api.Assertions.assertThat(error.toString())
        .doesNotContain("Exception", "at io.", KEY_A);
    coveredBySdkFixtures(events);
  }

  @Test
  void v2ErrorsCarryCategoryAndRetryableAndV1RefusalsKeepTheV1Shape() throws Exception {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .header("X-Request-Timeout-Ms", "soon")
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(400)
        .body("code", equalTo("BAD_REQUEST"))
        .body("category", equalTo("VALIDATION"))
        .body("retryable", equalTo(false))
        .body("requestId", not(emptyOrNullString()));
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY, "requestId", "bad id with spaces"))
        .post("/api/v2/search")
        .then()
        .statusCode(400)
        .body("message", containsString("requestId"));
    // v1 in an authenticated deployment: refused with the public error model, v1 fields only.
    var v1 =
        given()
            .header("Authorization", "Bearer " + KEY_A)
            .contentType("application/json")
            .body(Map.of("query", "shoes"))
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(409)
            .body("code", equalTo("TENANT_API_REQUIRED"))
            .extract()
            .jsonPath()
            .<String, Object>getMap("");
    org.assertj.core.api.Assertions.assertThat(v1.keySet())
        .containsExactlyInAnyOrder(
            "timestamp", "status", "error", "code", "message", "path", "requestId");
    // Released v1 SDKs parse errors strictly: the SDK fixture must hold exactly these fields.
    var fixture =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(SDK_CONTRACT.resolve("v1-api-error.json").toFile());
    org.assertj.core.api.Assertions.assertThat(fieldNames(List.of(fixture)))
        .isEqualTo(new java.util.TreeSet<>(v1.keySet()));
  }

  @Test
  void theVersionEndpointPublishesContractsAndLimits() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .get("/api/v2/version")
        .then()
        .statusCode(200)
        .body("apiVersions", hasItems("v1", "v2"))
        .body("contracts.error", equalTo("api-error/2"))
        .body("contracts.sse", equalTo("progress-event/2"))
        .body("limits.maxPageSize", equalTo(100))
        .body("limits.requestTimeoutMs", greaterThan(0));
    given().get("/api/v2/version").then().statusCode(401);
  }

  /** Blocks the (mocked) OpenSearch call inside the real connector guard until interrupted. */
  private java.util.concurrent.CountDownLatch[] blockBackend() {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var interrupted = new java.util.concurrent.CountDownLatch(1);
    when(backend.search(anyString(), anyMap()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              try {
                Thread.sleep(30_000);
              } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
              }
              return hits("shopA", 99);
            });
    return new java.util.concurrent.CountDownLatch[] {entered, interrupted};
  }

  @Test
  void anExpiredRequestStopsTheBackendCallAndFailsAsTimeout() throws Exception {
    var latches = blockBackend();
    long started = System.nanoTime();
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .header("X-Request-Timeout-Ms", "500")
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(504)
        .body("code", equalTo("TIMEOUT"))
        .body("category", equalTo("TIMEOUT"))
        .body("retryable", equalTo(true));
    long ms = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    org.assertj.core.api.Assertions.assertThat(ms).isLessThan(10_000);
    org.assertj.core.api.Assertions.assertThat(
            latches[1].await(5, java.util.concurrent.TimeUnit.SECONDS))
        .as("the backend call itself was interrupted")
        .isTrue();

    var stream =
        events(
            given()
                .header("Authorization", "Bearer " + KEY_A)
                .header("X-Request-Timeout-Ms", "500")
                .contentType("application/json")
                .body(Map.of("query", QUERY))
                .post("/api/v2/search/stream")
                .then()
                .statusCode(200)
                .extract()
                .asString());
    var last = stream.get(stream.size() - 1);
    org.assertj.core.api.Assertions.assertThat(last.get("kind").asText())
        .isEqualTo("search.failed");
    org.assertj.core.api.Assertions.assertThat(last.at("/error/code").asText())
        .isEqualTo("TIMEOUT");
  }

  @Test
  void onlyTheOwnerCanCancelAnInFlightRequestAndItFailsAsCancelled() throws Exception {
    var latches = blockBackend();
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var search =
          executor.submit(
              () ->
                  given()
                      .header("Authorization", "Bearer " + KEY_A)
                      .contentType("application/json")
                      .body(Map.of("query", QUERY, "requestId", "cancel-me-1"))
                      .post("/api/v2/search")
                      .then()
                      .extract());
      org.assertj.core.api.Assertions.assertThat(
              latches[0].await(10, java.util.concurrent.TimeUnit.SECONDS))
          .isTrue();
      // Another tenant cannot see or cancel it.
      given()
          .header("Authorization", "Bearer " + KEY_B)
          .delete("/api/v2/requests/cancel-me-1")
          .then()
          .statusCode(404)
          .body("code", equalTo("NOT_FOUND"));
      given()
          .header("Authorization", "Bearer " + KEY_A)
          .delete("/api/v2/requests/cancel-me-1")
          .then()
          .statusCode(200)
          .body("cancelled", equalTo(true));
      var result = search.get(10, java.util.concurrent.TimeUnit.SECONDS);
      org.assertj.core.api.Assertions.assertThat(result.statusCode()).isEqualTo(409);
      org.assertj.core.api.Assertions.assertThat(result.jsonPath().getString("code"))
          .isEqualTo("CANCELLED");
      org.assertj.core.api.Assertions.assertThat(
              latches[1].await(5, java.util.concurrent.TimeUnit.SECONDS))
          .isTrue();
    }
    // Finished requests are no longer cancellable.
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .delete("/api/v2/requests/cancel-me-1")
        .then()
        .statusCode(404);
  }

  /**
   * QB-189: real v2 search responses (apiVersion 2 behaviour and the planned contract) are written
   * to target/contract, and every field they contain must be in the SDK fixtures, which the Java
   * and TypeScript SDK tests map field by field onto their typed models.
   */
  @Test
  void realSearchResponsesAreCoveredByTheSdkModelFixtures() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "contract"));
    for (var entry :
        Map.of(
                "v2-search-response.json",
                Map.<String, Object>of("query", QUERY),
                "v2-search-response-planned.json",
                Map.<String, Object>of("query", QUERY, "contract", "commerce-search/1.0"))
            .entrySet()) {
      String body =
          given()
              .header("Authorization", "Bearer " + KEY_A)
              .contentType("application/json")
              .body(entry.getValue())
              .post("/api/v2/search")
              .then()
              .statusCode(200)
              .extract()
              .asString();
      java.nio.file.Files.writeString(
          java.nio.file.Path.of("target", "contract", entry.getKey()), body);
      var server = mapper.readTree(body);
      var fixturePath = SDK_CONTRACT.resolve(entry.getKey());
      org.assertj.core.api.Assertions.assertThat(fixturePath)
          .as("SDK fixture for " + entry.getKey() + " (copy from target/contract)")
          .exists();
      var fixture = mapper.readTree(fixturePath.toFile());
      org.assertj.core.api.Assertions.assertThat(fieldNames(List.of(fixture)))
          .as(entry.getKey())
          .containsAll(fieldNames(List.of(server)));
    }
  }

  /** QB-211 – QB-213: server-side histograms and categorized error counters are exported. */
  @Test
  void latencyHistogramsAndCategorizedErrorsAreExported() {
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(200);
    given()
        .header("Authorization", "Bearer " + KEY_A)
        .header("X-Request-Timeout-Ms", "nope")
        .contentType("application/json")
        .body(Map.of("query", QUERY))
        .post("/api/v2/search")
        .then()
        .statusCode(400);
    String metrics = given().get("/q/metrics").then().statusCode(200).extract().asString();
    org.assertj.core.api.Assertions.assertThat(metrics)
        .contains("qwenbridge_search_stage_seconds_bucket{")
        .contains("qwenbridge_search_duration_seconds_bucket{")
        .contains("qwenbridge_api_inflight")
        .containsPattern(
            "qwenbridge_api_errors_total\\{[^}]*category=\"VALIDATION\"[^}]*class=\"expected\"");
  }
}
