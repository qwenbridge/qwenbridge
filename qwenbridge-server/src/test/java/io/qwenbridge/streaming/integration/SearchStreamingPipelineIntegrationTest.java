package io.qwenbridge.streaming.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.streaming.ai.AIStreamingEventPublisher;
import io.qwenbridge.streaming.session.SessionSink;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SearchStreamingPipelineIntegrationTest {

  @Inject StreamingSessionRegistry registry;

  @Inject AIStreamingEventPublisher aiStreamingEventPublisher;

  @InjectMock AIService aiService;

  @InjectMock OpenSearchClient openSearchClient;

  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void resetMocks() {
    reset(aiService, openSearchClient, searchAnalysisService);
  }

  @AfterEach
  void tearDown() {
    registry.clear();
  }

  @Test
  void shouldCompleteOnlyStreamSessionsForMatchingPipelineRequest() {
    String requestId = "stream-request-1";
    String unrelatedRequestId = "stream-request-2";

    registry.register(requestId);
    registry.register(unrelatedRequestId);

    assertThat(registry.findByRequestId(requestId)).hasSize(1);
    assertThat(registry.findByRequestId(unrelatedRequestId)).hasSize(1);

    stubSuccessfulPipeline();

    postAnalyze(
            """
            {
              "requestId": "%s",
              "query": "table"
            }
            """
                .formatted(requestId))
        .statusCode(200);

    assertThat(registry.findByRequestId(requestId)).isEmpty();
    assertThat(registry.findByRequestId(unrelatedRequestId)).hasSize(1);
  }

  @Test
  void shouldPublishAiEventsBeforeTerminalPipelineCompletedEvent() {
    String requestId = "stream-order-1";
    RecordingSink sink = new RecordingSink();
    registry.register(requestId, sink);
    sink.send(null, "stream.connected", null);

    when(searchAnalysisService.analyze("table", requestId))
        .thenAnswer(
            invocation -> {
              aiStreamingEventPublisher.token(requestId, 1L, "hel");
              aiStreamingEventPublisher.token(requestId, 2L, "lo");
              aiStreamingEventPublisher.completed(requestId, 2L);
              return searchAnalysis();
            });

    when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());

    postAnalyze(
            """
            {
              "requestId": "%s",
              "query": "table"
            }
            """
                .formatted(requestId))
        .statusCode(200);

    assertThat(sink.events())
        .contains(
            "stream.connected",
            "pipeline.started",
            "ai.token",
            "ai.completed",
            "pipeline.completed");

    assertThat(sink.events().indexOf("pipeline.started")).isLessThan(sink.events().indexOf("ai.token"));
    assertThat(sink.events().indexOf("ai.token")).isLessThan(sink.events().indexOf("ai.completed"));
    assertThat(sink.events().indexOf("ai.completed"))
        .isLessThan(sink.events().indexOf("pipeline.completed"));

    assertThat(registry.findByRequestId(requestId)).isEmpty();
  }

  private io.restassured.response.ValidatableResponse postAnalyze(String body) {
    return given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/v1/search/analyze")
        .then();
  }

  private void stubSuccessfulPipeline() {
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(analysisJson()));
    when(searchAnalysisService.analyze("table")).thenReturn(searchAnalysis());
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(searchAnalysis());
    when(openSearchClient.search(anyString(), anyMap())).thenReturn(emptyOpenSearchResponse());
  }

  private SearchAnalysis searchAnalysis() {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(0.90)
        .intentReason("User is searching for a product.")
        .rewrites(List.of("table"))
        .semanticValidated(true)
        .semanticScore(0.85)
        .semanticMeaning("Product search query.")
        .entities(List.of("table"))
        .searchMode(SearchMode.KEYWORD)
        .backend(SearchBackend.OPENSEARCH)
        .keywordSearch(true)
        .vectorSearch(false)
        .hybridSearch(false)
        .facets(true)
        .rerank(false)
        .rewriteAgain(false)
        .answer(false)
        .decisionConfidence(0.85)
        .decisionReason("Use OpenSearch keyword search.")
        .build();
  }

  private String analysisJson() {
    return """
           {
             "language": "en",
             "intent": "PRODUCT_SEARCH",
             "intentConfidence": 0.85,
             "intentReason": "Product search.",
             "rewrites": ["table"],
             "semanticValidated": true,
             "semanticScore": 0.90,
             "semanticMeaning": "Product search.",
             "entities": ["table"],
             "searchMode": "KEYWORD",
             "backend": "OPENSEARCH",
             "keywordSearch": true,
             "vectorSearch": false,
             "hybridSearch": false,
             "facets": true,
             "rerank": false,
             "rewriteAgain": false,
             "answer": false,
             "decisionConfidence": 0.80,
             "decisionReason": "Keyword search is enough."
           }
           """;
  }

  private Map<String, Object> emptyOpenSearchResponse() {
    return Map.of(
        "took",
        0,
        "hits",
        Map.of(
            "total", Map.of("value", 0),
            "hits", List.of()));
  }

  private static final class RecordingSink implements SessionSink {
    private final List<String> events = new ArrayList<>();
    private boolean closed;

    @Override
    public void send(String eventId, String eventName, Object payload) {
      events.add(eventName);
    }

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    List<String> events() {
      return events;
    }
  }
}
