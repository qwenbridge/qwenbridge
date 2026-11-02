package io.qwenbridge.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QB-002: the Java SDK sends and receives multilingual text as strict UTF-8 without loss, using the
 * shared corpus from qwenbridge-core.
 */
class UnicodeRoundTripTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicReference<String> received = new AtomicReference<>();
  private static HttpServer server;

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/search/analyze",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          String text;
          try {
            text =
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
          } catch (CharacterCodingException e) {
            text = "<<invalid utf-8>>";
          }
          JsonNode request = JSON.readTree(text);
          String query = request.path("query").asText();
          received.set(query);
          byte[] response =
              JSON.writeValueAsBytes(
                  Map.of(
                      "requestId",
                      request.path("requestId").asText("r"),
                      "originalQuery",
                      query,
                      "language",
                      "und",
                      "policyPassed",
                      true));
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  static Stream<String> corpusQueries() throws IOException {
    Path path =
        Path.of(
            "..",
            "qwenbridge-core",
            "src",
            "test",
            "resources",
            "corpus",
            "multilingual-input-corpus.json");
    JsonNode root = JSON.readTree(Files.readAllBytes(path));
    List<String> queries = new ArrayList<>();
    for (JsonNode node : root.get("cases")) {
      if ("VALID".equals(node.path("expect").asText())) {
        queries.add(node.get("query").asText());
      }
    }
    return queries.stream();
  }

  @ParameterizedTest
  @MethodSource("corpusQueries")
  void queryRoundTripsThroughSdkAsStrictUtf8(String query) {
    QwenBridgeClient client =
        new QwenBridgeClient(
            new QwenBridgeClientConfig(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)));

    SearchAnalyzeResponse response =
        client.analyze(SearchAnalyzeRequest.withRequestId("sdk-unicode", query));

    assertEquals(query, received.get(), "server must receive the exact query");
    assertEquals(query, response.originalQuery(), "SDK must decode the exact query");
    assertArrayEquals(
        query.getBytes(StandardCharsets.UTF_8),
        response.originalQuery().getBytes(StandardCharsets.UTF_8));
  }
}
