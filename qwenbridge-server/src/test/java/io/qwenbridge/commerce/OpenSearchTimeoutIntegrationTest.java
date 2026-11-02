package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import jakarta.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OpenSearchTimeoutIntegrationTest.SlowBackend.class)
class OpenSearchTimeoutIntegrationTest {
  private static HttpServer server;

  public static class SlowBackend implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      try {
        if (server == null) {
          server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
          server.createContext(
              "/",
              exchange -> {
                try {
                  exchange.getRequestBody().readAllBytes();
                  Thread.sleep(500);
                  exchange.sendResponseHeaders(200, 2);
                  exchange
                      .getResponseBody()
                      .write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                } finally {
                  exchange.close();
                }
              });
          server.start();
        }
        return Map.of(
            "qwenbridge.search.opensearch.base-url",
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "quarkus.rest-client.opensearch.read-timeout",
            "100");
      } catch (java.io.IOException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  @Inject @RestClient OpenSearchApiClient api;

  @Test
  void slowHttpBackendTimesOutBeforeItsResponseCompletes() {
    long started = System.nanoTime();
    assertThatThrownBy(() -> api.search("products", Map.of("query", Map.of("match_all", Map.of()))))
        .isInstanceOf(jakarta.ws.rs.ProcessingException.class);
    assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2000);
  }

  @AfterAll
  static void stop() {
    if (server != null) server.stop(0);
  }
}
