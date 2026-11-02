package io.qwenbridge.streaming.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
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
import io.qwenbridge.streaming.session.SessionSink;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import io.qwenbridge.testsupport.SharedMultilingualCorpus;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QB-002/006: multilingual text and the request ID survive the REST → pipeline → SSE path. Each
 * pipeline event payload is serialized with the application's ObjectMapper (as the SSE writer does)
 * and the snapshot {@code query} must equal the original input exactly.
 */
@QuarkusTest
class UnicodeStreamingRoundTripTest {

  @Inject StreamingSessionRegistry registry;
  @Inject ObjectMapper objectMapper;

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void stub() {
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

  @AfterEach
  void clear() {
    registry.clear();
  }

  static Stream<SharedMultilingualCorpus.Case> validCases() {
    return SharedMultilingualCorpus.load().stream()
        .filter(SharedMultilingualCorpus.Case::expectValid);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validCases")
  void snapshotQueryAndRequestIdSurviveSseSerialization(SharedMultilingualCorpus.Case c)
      throws Exception {
    String requestId = "unicode-sse-" + c.id();
    CapturingSink sink = new CapturingSink();
    registry.register(requestId, sink);

    var body = c.body().deepCopy();
    body.put("requestId", requestId);
    given()
        .contentType(ContentType.JSON)
        .body(body.toString().getBytes(StandardCharsets.UTF_8))
        .post("/api/v1/search/analyze")
        .then()
        .statusCode(200);

    List<JsonNode> snapshots = new ArrayList<>();
    for (Object payload : sink.payloads) {
      byte[] wire = objectMapper.writeValueAsBytes(payload);
      JsonNode node = objectMapper.readTree(new String(wire, StandardCharsets.UTF_8));
      JsonNode query = node.findValue("query");
      if (query != null) {
        snapshots.add(node);
        assertThat(query.asText()).as(c.id()).isEqualTo(c.query());
      }
      JsonNode eventRequestId = node.findValue("requestId");
      if (eventRequestId != null) {
        assertThat(eventRequestId.asText()).isEqualTo(requestId);
      }
    }
    assertThat(snapshots).as("pipeline events with a query snapshot").isNotEmpty();
  }

  private static final class CapturingSink implements SessionSink {
    final List<Object> payloads = new ArrayList<>();
    private boolean closed;

    @Override
    public synchronized void send(String eventId, String eventName, Object payload) {
      if (payload != null) {
        payloads.add(payload);
      }
    }

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
