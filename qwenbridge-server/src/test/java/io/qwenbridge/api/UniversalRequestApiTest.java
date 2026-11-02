package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.testsupport.SharedMultilingualCorpus;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QB-001/002/005/007 through the real REST stack: the shared multilingual corpus is posted to the
 * v1 analyze endpoint; valid inputs must come back byte-identical, invalid ones must be rejected
 * with 400 before any AI work happens.
 */
@QuarkusTest
class UniversalRequestApiTest {

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void stubPipeline() {
    reset(aiService, openSearchClient, searchAnalysisService);
    SearchAnalysis analysis =
        SearchAnalysis.builder()
            .language("unknown")
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(0.9)
            .intentReason("test")
            .rewrites(List.of("q"))
            .semanticValidated(true)
            .semanticScore(0.8)
            .semanticMeaning("test")
            .entities(List.of())
            .searchMode(SearchMode.KEYWORD)
            .backend(SearchBackend.OPENSEARCH)
            .keywordSearch(true)
            .vectorSearch(false)
            .hybridSearch(false)
            .facets(false)
            .rerank(false)
            .rewriteAgain(false)
            .answer(false)
            .decisionConfidence(0.8)
            .decisionReason("test")
            .build();
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("{}"));
    when(searchAnalysisService.analyze(anyString())).thenReturn(analysis);
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(analysis);
    when(openSearchClient.search(anyString(), anyMap()))
        .thenReturn(
            Map.of("took", 0, "hits", Map.of("total", Map.of("value", 0), "hits", List.of())));
  }

  static Stream<SharedMultilingualCorpus.Case> validCases() {
    return SharedMultilingualCorpus.load().stream()
        .filter(SharedMultilingualCorpus.Case::expectValid);
  }

  static Stream<SharedMultilingualCorpus.Case> invalidCases() {
    return SharedMultilingualCorpus.load().stream().filter(c -> !c.expectValid());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validCases")
  void validCorpusInputRoundTripsByteForByte(SharedMultilingualCorpus.Case c) {
    Response response =
        given()
            .contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
            .body(c.body().toString().getBytes(StandardCharsets.UTF_8))
            .post("/api/v1/search/analyze");

    assertThat(response.statusCode()).as(c.id()).isEqualTo(200);
    assertThat(response.contentType()).containsIgnoringCase("application/json");
    String originalQuery = response.jsonPath().getString("originalQuery");
    assertThat(originalQuery).as(c.id()).isEqualTo(c.query());
    assertThat(originalQuery.getBytes(StandardCharsets.UTF_8))
        .as(c.id())
        .isEqualTo(c.query().getBytes(StandardCharsets.UTF_8));
    // Legitimate orthography (Persian half-space, emoji ZWJ sequences) is not "obfuscation".
    assertThat(response.jsonPath().getList("threatReasons", String.class))
        .as(c.id())
        .noneMatch(reason -> reason.contains("Zero-width") || reason.contains("format control"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCases")
  void invalidCorpusInputIsRejectedBeforeAnyAiCall(SharedMultilingualCorpus.Case c) {
    given()
        .contentType(ContentType.JSON)
        .body(c.body().toString().getBytes(StandardCharsets.UTF_8))
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(400)
        .body("code", equalTo("VALIDATION_ERROR"));
    org.mockito.Mockito.verifyNoInteractions(aiService, searchAnalysisService, openSearchClient);
  }

  @Test
  void overlongQueryIsRejectedWithoutEchoingIt() {
    String query = "ک".repeat(2049);
    String body =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(400)
            .body("code", equalTo("VALIDATION_ERROR"))
            .extract()
            .asString();
    assertThat(body).doesNotContain("ککک");
  }

  @Test
  void deeplyNestedJsonIsRejectedAs400NotServerError() {
    String nested = "[".repeat(200) + "]".repeat(200);
    given()
        .contentType(ContentType.JSON)
        .body("{\"query\":\"table\",\"unknownField\":" + nested + "}")
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(400);
    org.mockito.Mockito.verifyNoInteractions(aiService, searchAnalysisService);
  }

  @Test
  void bcp47RegionTagsAreNowAcceptedAndLegacyPipelineReceivesPrimaryLanguage() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("query", "میز", "declaredLanguage", "fa-IR", "locale", "fa-ir"))
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200)
        .body("language", equalTo("fa"));
  }

  @Test
  void domainNeutralFieldsAreAccepted() {
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "contractVersion",
                1,
                "query",
                "pods with high memory",
                "domainHints",
                List.of("devops"),
                "context",
                Map.of("channel", "cli")))
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200);
  }
}
