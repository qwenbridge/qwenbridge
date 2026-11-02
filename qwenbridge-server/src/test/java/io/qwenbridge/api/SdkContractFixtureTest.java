package io.qwenbridge.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Server half of the v1 analyze contract test (QB-002, QB-185). The real response produced by the
 * running Quarkus application is written to {@code target/contract/v1-analyze-response.json}, and
 * every top-level field it contains must also be present in the Java SDK's golden fixture ({@code
 * qwenbridge-java-sdk/src/test/resources/contract/v1-analyze-response.json}), which the SDK's own
 * tests parse. Adding a response field therefore forces the fixture, and with it the SDK
 * compatibility test, to be updated.
 */
@QuarkusTest
class SdkContractFixtureTest {

  private static final Path SDK_FIXTURE =
      Path.of(
          "..",
          "qwenbridge-java-sdk",
          "src",
          "test",
          "resources",
          "contract",
          "v1-analyze-response.json");

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @Test
  void everyServerResponseFieldIsCoveredByTheSdkFixture() throws Exception {
    SearchAnalysis analysis =
        SearchAnalysis.builder()
            .language("fa")
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(0.9)
            .intentReason("test")
            .rewrites(List.of("black shoes"))
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

    String body =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("requestId", "contract-1", "query", "کفش مشکی"))
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    Path actual = Path.of("target", "contract", "v1-analyze-response.json");
    Files.createDirectories(actual.getParent());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode response = mapper.readTree(body);
    Files.writeString(actual, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

    assertThat(SDK_FIXTURE).as("SDK golden fixture").exists();
    Set<String> serverFields = fieldNames(response);
    Set<String> fixtureFields = fieldNames(mapper.readTree(Files.readAllBytes(SDK_FIXTURE)));
    assertThat(fixtureFields)
        .as(
            "fields returned by the server but missing from the SDK fixture; refresh it from %s",
            actual)
        .containsAll(serverFields);
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
