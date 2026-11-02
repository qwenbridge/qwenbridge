package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogAdapterTest {
  @Test
  void httpFeedRetriesTransientFailuresAndPreservesDeletionAndCursor() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    server.createContext(
        "/feed",
        exchange -> {
          if (!"Bearer test-feed".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          if (calls.incrementAndGet() == 1) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
          }
          byte[] bytes =
              "{\"changes\":[{\"id\":\"sku-1\",\"deleted\":true,\"document\":{}}],\"nextCursor\":\"next\",\"hasMore\":true}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var connector =
        new HttpCatalogConnector(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/feed"),
            "test-feed",
            new ObjectMapper())) {
      var page = connector.fetch(null);
      assertThat(calls.get()).isEqualTo(2);
      assertThat(page.nextCursor()).isEqualTo("next");
      assertThat(page.changes().getFirst().deleted()).isTrue();
    } finally {
      server.stop(0);
    }
  }
}
