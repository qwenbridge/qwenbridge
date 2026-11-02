package io.qwenbridge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * SDK half of the v1 analyze contract test (QB-002, QB-189). The fixture is a real response
 * captured from the server by {@code SdkContractFixtureTest}; the server test fails whenever the
 * server returns a field the fixture does not contain. Before this test existed, the released SDK
 * failed on every real response because the server had added {@code clarificationQuestions}.
 */
class ServerContractCompatibilityTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void realServerResponseIsParsedByTheSdk() throws Exception {
    byte[] fixture = fixture();
    SearchAnalyzeResponse response = analyzeAgainst(fixture);

    assertEquals("contract-1", response.requestId());
    assertEquals("کفش مشکی", response.originalQuery());
    assertEquals("fa", response.language());
    assertNotNull(response.decision());
  }

  @Test
  void fieldsAddedByFutureServersAreIgnored() throws Exception {
    ObjectNode future = (ObjectNode) JSON.readTree(fixture());
    future.put("someFutureField", "value");
    future.putObject("anotherFutureObject").put("nested", true);

    SearchAnalyzeResponse response = analyzeAgainst(JSON.writeValueAsBytes(future));

    assertEquals("contract-1", response.requestId());
    assertEquals("fa", response.language());
  }

  private SearchAnalyzeResponse analyzeAgainst(byte[] body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/search/analyze",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    QwenBridgeClient client =
        new QwenBridgeClient(
            new QwenBridgeClientConfig(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)));
    return client.analyze(SearchAnalyzeRequest.withRequestId("contract-1", "q"));
  }

  private static byte[] fixture() throws Exception {
    try (InputStream in =
        ServerContractCompatibilityTest.class.getResourceAsStream(
            "/contract/v1-analyze-response.json")) {
      assertNotNull(in, "contract fixture missing");
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return text.getBytes(StandardCharsets.UTF_8);
    }
  }
}
