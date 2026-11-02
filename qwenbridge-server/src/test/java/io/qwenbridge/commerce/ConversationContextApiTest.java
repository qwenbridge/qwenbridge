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
import io.qwenbridge.platform.PlatformStore;
import jakarta.inject.Inject;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-139 – QB-146 end to end, Redis-free: the platform store is the memory adapter and the backend
 * is the in-process one. Two users share tenant shopA; a third is in shopB.
 */
@QuarkusTest
@TestProfile(ConversationContextApiTest.Profile.class)
class ConversationContextApiTest {
  static final String ALICE = "test-only-context-alice-1234567";
  static final String CAROL = "test-only-context-carol-1234567";
  static final String BOB = "test-only-context-bob-123456789";
  static final String FIRST = "Nike shoes under 100 EUR";
  static final String SECOND = "only black ones";

  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      try {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var config = new HashMap<String, String>();
        config.put("qwenbridge.commerce.backend", "memory");
        config.put("qwenbridge.embedding.enabled", "false");
        config.put("qwenbridge.platform.store", "memory");
        config.put(
            "qwenbridge.context.signing.private-key",
            Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        config.put(
            "qwenbridge.context.signing.public-key",
            Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        config.put("qwenbridge.security.authentication-required", "true");
        config.put(
            "qwenbridge.security.api-principals",
            "[{\"key\":\""
                + ALICE
                + "\",\"subject\":\"alice\",\"tenant\":\"shopA\",\"roles\":[\"search\"]},"
                + "{\"key\":\""
                + CAROL
                + "\",\"subject\":\"carol\",\"tenant\":\"shopA\",\"roles\":[\"search\"]},"
                + "{\"key\":\""
                + BOB
                + "\",\"subject\":\"bob\",\"tenant\":\"shopB\",\"roles\":[\"search\"]}]");
        return config;
      } catch (java.security.GeneralSecurityException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @InjectMock SearchAnalysisService analysis;
  @InjectMock OpenSearchClient opensearch;
  @InjectMock CommerceEvidenceStore evidence;
  @InjectMock CommerceAnalytics analytics;
  @InjectMock io.qwenbridge.ai.service.AIService ai;
  @Inject CommerceBackends backends;
  @Inject PlatformStore store;

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
    for (String tenant : List.of("shopA", "shopB"))
      SyntheticCatalogSearchTest.CATALOG.forEach(
          (id, raw) -> {
            var product = normalizer.normalize("http-json", id, raw);
            backends.upsert(
                tenant, id, normalizer.document(product, product.variants().getFirst(), tenant));
          });
    when(analysis.analyze(FIRST))
        .thenReturn(
            model(
                FIRST,
                List.of(
                    c(FIRST, "brand", Operator.EQ, "Nike", "Nike"),
                    c(FIRST, "price", Operator.LT, "100", "under 100 EUR"),
                    c(FIRST, "currency", Operator.EQ, "EUR", "EUR"))));
    when(analysis.analyze(SECOND))
        .thenReturn(model(SECOND, List.of(c(SECOND, "color", Operator.EQ, "black", "black"))));
    var n = new AtomicInteger();
    when(evidence.recordQuery(
            anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(i -> attribution(i.getArgument(0), i.getArgument(1), n));
    when(evidence.record(anyString(), anyString(), anyString(), anyList(), nullable(String.class)))
        .thenAnswer(i -> attribution(i.getArgument(0), i.getArgument(1), n));
  }

  static Attribution attribution(String tenant, String source, AtomicInteger n) {
    return new Attribution(
        String.format("%064d", n.incrementAndGet()),
        tenant,
        "",
        source,
        Instant.now(),
        Instant.now(),
        "c",
        List.of(),
        null);
  }

  private io.restassured.response.ValidatableResponse turn(String key, Map<String, Object> body) {
    return given()
        .header("Authorization", "Bearer " + key)
        .contentType("application/json")
        .body(body)
        .post("/api/v2/conversations/turns")
        .then();
  }

  @Test
  void signedStatelessTurnsCarryConstraintsWithProvenance() {
    var first =
        turn(ALICE, Map.of("query", FIRST))
            .statusCode(200)
            .body("response.status", equalTo("OK"))
            .body("context.version", equalTo("conversation-context/1.0"))
            .body("context.tenant", equalTo("shopA"))
            .body("context.subject", equalTo("alice"))
            .body("context.revision", equalTo(1))
            .body("context.constraints.origin", everyItem(equalTo("USER_TEXT")))
            .body("context.constraints.turn", everyItem(equalTo(1)))
            .body("token", org.hamcrest.Matchers.startsWith("v1."))
            .extract()
            .jsonPath();
    String token = first.getString("token");
    turn(ALICE, Map.of("query", SECOND, "contextToken", token))
        .statusCode(200)
        .body("response.status", equalTo("OK"))
        .body("response.results.hits.document.color", everyItem(equalTo("black")))
        .body("response.results.hits.document.brand", everyItem(equalTo("Nike")))
        .body("response.results.hits.id", not(hasItem("s3")))
        .body("context.revision", equalTo(2))
        .body("context.turns", hasSize(2))
        .body("context.constraints.find { it.field == 'brand' }.turn", equalTo(1))
        .body("context.constraints.find { it.field == 'brand' }.origin", equalTo("USER_TEXT"))
        .body("context.constraints.find { it.field == 'color' }.turn", equalTo(2))
        .body("context.constraints.find { it.field == 'color' }.origin", equalTo("USER_TEXT"));
  }

  @Test
  void plainClientContextIsRevalidatedAndDowngraded() {
    Map<String, Object> ctx =
        turn(ALICE, Map.of("query", FIRST)).statusCode(200).extract().jsonPath().getMap("context");
    turn(ALICE, Map.of("query", SECOND, "context", ctx))
        .statusCode(200)
        .body("context.constraints.find { it.field == 'brand' }.origin", equalTo("CONTEXT"))
        .body("context.constraints.find { it.field == 'color' }.origin", equalTo("USER_TEXT"));
    // A client cannot smuggle a field the schema does not have.
    var forged = new HashMap<>(ctx);
    var constraints = new ArrayList<Object>((List<?>) ctx.get("constraints"));
    constraints.add(
        Map.of(
            "field",
            "internalCost",
            "operator",
            "LT",
            "value",
            "1",
            "origin",
            "USER_TEXT",
            "turn",
            1));
    forged.put("constraints", constraints);
    turn(ALICE, Map.of("query", SECOND, "context", forged))
        .statusCode(400)
        .body("message", containsString("UNKNOWN_FIELD"));
    // Nor grow it without bound.
    var huge = new HashMap<>(ctx);
    var turns = new ArrayList<Object>();
    for (int i = 0; i < 400; i++) turns.add(Map.of("requestId", "r" + i, "status", "OK"));
    huge.put("turns", turns);
    turn(ALICE, Map.of("query", SECOND, "context", huge)).statusCode(413);
  }

  @Test
  void contextNeverCrossesUsersOrTenants() {
    var first = turn(ALICE, Map.of("query", FIRST)).statusCode(200).extract().jsonPath();
    String token = first.getString("token");
    Map<String, Object> ctx = first.getMap("context");
    turn(CAROL, Map.of("query", SECOND, "contextToken", token))
        .statusCode(403)
        .body("message", containsString("FOREIGN"));
    turn(BOB, Map.of("query", SECOND, "contextToken", token)).statusCode(403);
    turn(CAROL, Map.of("query", SECOND, "context", ctx)).statusCode(403);
    var otherTenant = new HashMap<>(ctx);
    otherTenant.put("tenant", "shopB");
    turn(ALICE, Map.of("query", SECOND, "context", otherTenant)).statusCode(403);
    // A token whose payload was edited fails its signature.
    String[] parts = token.split("\\.");
    String payload =
        new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\"alice\"", "\"carol\"");
    String forged =
        "v1."
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + "."
            + parts[2];
    turn(CAROL, Map.of("query", SECOND, "contextToken", forged))
        .statusCode(400)
        .body("message", containsString("BAD_SIGNATURE"));
    turn(ALICE, Map.of("query", SECOND, "contextToken", token, "context", ctx)).statusCode(400);
  }

  @Test
  void sessionContextIsTheSameContractAndOwnerOnly() {
    String session =
        given()
            .header("Authorization", "Bearer " + ALICE)
            .contentType("application/json")
            .body(Map.of())
            .post("/api/v2/sessions")
            .then()
            .statusCode(200)
            .extract()
            .path("id");
    given()
        .header("Authorization", "Bearer " + ALICE)
        .contentType("application/json")
        .body(Map.of("query", FIRST))
        .post("/api/v2/sessions/" + session + "/turns")
        .then()
        .statusCode(200);
    org.assertj.core.api.Assertions.assertThat(store.adapter()).isEqualTo("memory");
    given()
        .header("Authorization", "Bearer " + ALICE)
        .get("/api/v2/sessions/" + session + "/context")
        .then()
        .statusCode(200)
        .body("context.version", equalTo("conversation-context/1.0"))
        .body("context.conversationId", equalTo(session))
        .body("context.constraints.field", containsInAnyOrder("brand", "price", "currency"))
        .body("context.turns", hasSize(1))
        .body("token", org.hamcrest.Matchers.startsWith("v1."));
    given()
        .header("Authorization", "Bearer " + CAROL)
        .get("/api/v2/sessions/" + session + "/context")
        .then()
        .statusCode(404);
    given()
        .header("Authorization", "Bearer " + BOB)
        .get("/api/v2/sessions/" + session + "/context")
        .then()
        .statusCode(404);
  }
}
