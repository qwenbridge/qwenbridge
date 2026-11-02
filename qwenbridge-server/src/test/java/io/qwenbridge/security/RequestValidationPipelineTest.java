package io.qwenbridge.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.pipeline.step.TranslationPipelineTest;
import io.restassured.response.Response;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-057, QB-058, QB-062, QB-063 over HTTP, with abuse protection disabled (the test profile): the
 * envelope, structural limits and safe error handling do not depend on it.
 */
@QuarkusTest
class RequestValidationPipelineTest {

  static final String ANALYZE = "/api/v1/search/analyze";

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void setUp() {
    TranslationPipelineTest.stub(aiService, openSearchClient, searchAnalysisService);
  }

  private static void assertSafeError(Response r) {
    String body = r.asString();
    assertThat(body)
        .doesNotContain("Exception")
        .doesNotContain("\tat ")
        .doesNotContain("io.qwenbridge.")
        .doesNotContain("com.fasterxml");
    assertThat(r.contentType()).as(body).startsWith("application/json");
    assertThat(r.jsonPath().getString("code")).as(body).isNotBlank();
  }

  @Test
  void bodyWithoutJsonContentTypeIsRejectedBeforeAnyWork() {
    Response r = given().contentType("text/plain").body("{\"query\":\"shoes\"}").post(ANALYZE);
    assertThat(r.statusCode()).isEqualTo(415);
    assertSafeError(r);
    verifyNoInteractions(searchAnalysisService);
  }

  @Test
  void oversizedBodyIsRejectedEvenWithAbuseProtectionDisabled() {
    Response r =
        given()
            .contentType("application/json")
            .body("{\"query\":\"" + "a".repeat(70_000) + "\"}")
            .post(ANALYZE);
    assertThat(r.statusCode()).isEqualTo(413);
    assertSafeError(r);
    verifyNoInteractions(searchAnalysisService);
  }

  @Test
  void uploadRoutesHaveTheirOwnLargerLimit() {
    Response r =
        given()
            .contentType("application/json")
            .body("{\"manifest\":\"" + "a".repeat(200_000) + "\"}")
            .post("/api/v2/platform/packs");
    assertThat(r.statusCode()).isNotEqualTo(413).isLessThan(500);
  }

  @Test
  void structuralLimitsRejectHugeNumbersAndDeepNesting() {
    Response number =
        given()
            .contentType("application/json")
            .body("{\"query\":\"x\",\"contractVersion\":" + "9".repeat(200) + "}")
            .post(ANALYZE);
    assertThat(number.statusCode()).isEqualTo(400);
    assertSafeError(number);

    Response deep =
        given()
            .contentType("application/json")
            .body("{\"context\":" + "[".repeat(100) + "]".repeat(100) + "}")
            .post(ANALYZE);
    assertThat(deep.statusCode()).isEqualTo(400);
    assertSafeError(deep);
  }

  @Test
  void malformedJsonGetsAStandardErrorWithoutParserDetails() {
    Response r = given().contentType("application/json").body("{\"query\": \"x\",,}").post(ANALYZE);
    assertThat(r.statusCode()).isEqualTo(400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("Malformed JSON request body");
    assertSafeError(r);
  }

  /**
   * Seeded HTTP fuzzing (QB-063): random, often malformed bodies never cause a server error, a
   * stack trace or an echo of the submitted body. Seed: {@code -Dqwenbridge.fuzz.seed}.
   */
  @Test
  void randomBodiesNeverCauseServerErrorsOrLeakDetails() {
    long seed = Long.getLong("qwenbridge.fuzz.seed", 20261031L);
    Random random = new Random(seed);
    String[] pieces = {
      "{",
      "}",
      "[",
      "]",
      ":",
      ",",
      "\"query\"",
      "\"context\"",
      "\"domainHints\"",
      "\"locale\"",
      "\"declaredLanguage\"",
      "\"contractVersion\"",
      "null",
      "true",
      "1e999",
      "-0",
      "\"\\u0000\"",
      "\"x\"",
      "\"کفش زیر ۱۰۰ یورو\"",
      "\"<script>\"",
      "\"' OR 1=1--\"",
      "\"" + "z".repeat(300) + "\"",
      "{\"a\":1}",
      "\"\\ud800\"",
      "\"en-US\"",
      "\"x_y\"",
      "123456789012345678901234567890"
    };
    Set<Integer> allowed = Set.of(200, 400, 413, 415, 422);
    for (int i = 0; i < 120; i++) {
      StringBuilder body = new StringBuilder();
      int parts = 1 + random.nextInt(14);
      for (int p = 0; p < parts; p++) {
        body.append(pieces[random.nextInt(pieces.length)]);
      }
      String marker = "fz" + Long.toHexString(random.nextLong());
      String payload = random.nextBoolean() ? body.toString() : body + "\"" + marker + "\"";
      Response r = given().contentType("application/json").body(payload).post(ANALYZE);
      String context = "seed=" + seed + " iteration=" + i + " body=" + payload;
      assertThat(r.statusCode()).as(context).isIn(allowed);
      if (r.statusCode() != 200) {
        assertThat(r.contentType()).as(context + " status=" + r.statusCode()).isNotBlank();
        assertThat(r.asString()).as(context).doesNotContain(marker);
        assertSafeError(r);
      }
    }
  }
}
