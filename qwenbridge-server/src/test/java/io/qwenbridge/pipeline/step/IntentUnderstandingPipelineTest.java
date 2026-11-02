package io.qwenbridge.pipeline.step;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
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
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-023–QB-028 through the REST pipeline. The AI analysis is mocked to be confident and agreeable,
 * so these tests prove that the deterministic assessment still governs clarification and that the
 * v1 {@code intent} field keeps its legacy values.
 */
@QuarkusTest
class IntentUnderstandingPipelineTest {

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void stub() {
    reset(aiService, openSearchClient, searchAnalysisService);
    SearchAnalysis confident =
        SearchAnalysis.builder()
            .language("en")
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(0.95)
            .intentReason("model is confident")
            .rewrites(List.of("q"))
            .semanticValidated(true)
            .semanticScore(0.9)
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
            .decisionConfidence(0.9)
            .decisionReason("test")
            .modelThreatDecision("ALLOW")
            .build();
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("{}"));
    when(searchAnalysisService.analyze(anyString())).thenReturn(confident);
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(confident);
    when(openSearchClient.search(anyString(), anyMap()))
        .thenReturn(
            Map.of("took", 0, "hits", Map.of("total", Map.of("value", 0), "hits", List.of())));
  }

  private Response analyze(Map<String, Object> body) {
    return given().contentType(ContentType.JSON).body(body).post("/api/v1/search/analyze");
  }

  @Test
  void vagueRequestIsClarifiedEvenWhenTheModelIsConfident() {
    analyze(Map.of("query", "Find the best option"))
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLARIFY"))
        .body("intentAnalysis.status", equalTo("NEEDS_CLARIFICATION"))
        .body("intentAnalysis.issues", hasItems("NO_SUBJECT"))
        .body(
            "clarificationQuestions[0]",
            equalTo(
                "What are you looking for? Please describe the kind of item or information you"
                    + " want."));
  }

  @Test
  void devopsRequestGetsTypedIntentsHintsAndKeepsLegacyIntent() {
    Response response =
        analyze(
            Map.of(
                "query",
                "Find Kubernetes pods with memory usage above 85% and then show their logs"));
    response.then().statusCode(200).body("intent", equalTo("PRODUCT_SEARCH"));
    assertThat(response.jsonPath().getList("intentAnalysis.intents.type", String.class))
        .containsExactly("search", "search");
    assertThat(response.jsonPath().getList("intentAnalysis.retrievalModes", String.class))
        .contains("STRUCTURED", "LEXICAL");
    assertThat(response.jsonPath().getList("intentAnalysis.subqueries[1].dependsOn", String.class))
        .containsExactly("s1");
    assertThat(response.jsonPath().getString("intentAnalysis.status")).isEqualTo("CLEAR");
  }

  @Test
  void requestForADomainThatIsNotEnabledIsOutOfDomain() {
    analyze(Map.of("query", "Find flights to Lisbon", "domainHints", List.of("travel")))
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLARIFY"))
        .body("intentAnalysis.status", equalTo("OUT_OF_DOMAIN"));
  }

  @Test
  void enabledDomainHintsAndPersianRequestsAreClear() {
    analyze(Map.of("query", "کفش مشکی می‌خوام", "domainHints", List.of("commerce")))
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .body("intentAnalysis.status", equalTo("CLEAR"))
        .body("intentAnalysis.intents[0].type", equalTo("search"));
  }
}
