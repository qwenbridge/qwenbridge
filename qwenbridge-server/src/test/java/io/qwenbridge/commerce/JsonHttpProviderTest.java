package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.ai.contract.*;
import io.qwenbridge.ai.provider.http.JsonHttpProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JsonHttpProviderTest {
  @Test
  void secondProviderUsesRealHttpForChatSchemaEmbeddingsAndStreaming() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var payload = new AtomicReference<String>();
    server.createContext(
        "/",
        exchange -> {
          payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          String result =
              switch (exchange.getRequestURI().getPath()) {
                case "/chat" -> "{\"content\":\"hello\"}";
                case "/embeddings" -> "{\"vector\":[0.1,0.2,0.3]}";
                case "/stream" ->
                    "{\"content\":\"hello\",\"done\":false}\n{\"content\":\"\",\"done\":true}\n";
                default -> throw new IllegalArgumentException();
              };
          if (!"Bearer gateway-test"
              .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var provider =
        new JsonHttpProvider(
            new ObjectMapper(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "gateway-test");
    try {
      assertThat(provider.chat(new ChatRequest("hi", Map.of("type", "object"))).content())
          .isEqualTo("hello");
      assertThat(payload.get()).contains("outputSchema");
      assertThat(provider.embed(new EmbeddingRequest("hi")).vector()).hasSize(3);
      var chunks =
          provider
              .streamChat(new StreamingChatRequest("hi"))
              .collect()
              .asList()
              .await()
              .atMost(java.time.Duration.ofSeconds(5));
      assertThat(chunks).hasSize(2);
      assertThat(chunks.getLast().done()).isTrue();
      assertThat(provider.manifest().capabilities()).contains("structured-output");
    } finally {
      provider.close();
      server.stop(0);
    }
  }
}
