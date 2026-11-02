package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import io.qwenbridge.security.ApiIdentity;
import jakarta.inject.Inject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_REDIS_IT", matches = "true")
class CatalogSynchronizationIntegrationTest {
  @Inject RedisDataSource redis;
  @Inject ObjectMapper mapper;

  /**
   * A complete-snapshot source removes variants missing from a later run and refreshes prices
   * (QB-287: formerly exercised through the removed Shopify connector; now through the test-only
   * {@code test-snapshot} kind).
   */
  @Test
  void completeSnapshotsRemoveMissingVariantsAndRefreshPrices() {
    io.qwenbridge.commerce.testconnector.SnapshotCatalogConnectorFactory.VARIANTS.set(2);
    var sink = mock(OpenSearchApiClient.class);
    var documents = new ConcurrentHashMap<String, Map<String, Object>>();
    when(sink.upsert(anyString(), anyString(), anyMap()))
        .thenAnswer(
            call -> {
              documents.put(call.getArgument(1), call.getArgument(2));
              return Map.of();
            });
    when(sink.delete(anyString(), anyString()))
        .thenAnswer(
            call -> {
              documents.remove(call.getArgument(1));
              return Map.of();
            });
    var ai = mock(io.qwenbridge.ai.service.AIService.class);
    when(ai.embed(any()))
        .thenReturn(
            new io.qwenbridge.ai.contract.EmbeddingResponse(
                java.util.Collections.nCopies(1024, 0.1)));
    var sync =
        new CatalogSynchronization(
            sink,
            new OpenSearchProperties(null, "test", 10, null, null),
            redis,
            mapper,
            ai,
            Optional.of("http://127.0.0.1:9/snapshot-" + java.util.UUID.randomUUID()),
            Optional.empty(),
            io.qwenbridge.commerce.testconnector.SnapshotCatalogConnectorFactory.KIND);
    var caller =
        new ApiIdentity.Principal(
            "alice", "shop-" + java.util.UUID.randomUUID(), Set.of("catalog:write"));
    assertThat(sync.synchronize(caller)).containsEntry("status", "COMPLETE");
    assertThat(documents).hasSize(2);
    io.qwenbridge.commerce.testconnector.SnapshotCatalogConnectorFactory.VARIANTS.set(1);
    assertThat(sync.synchronize(caller)).containsEntry("status", "COMPLETE");
    assertThat(documents).hasSize(1);
    assertThat(documents.values().iterator().next().get("price").toString()).isEqualTo("88");
    assertThat(documents.values().iterator().next())
        .containsEntry("currency", "USD")
        .containsEntry("productTextTemplate", "commerce-product-text/1")
        .containsKey("productTextFingerprint")
        .containsKey("embedding");
  }

  @Test
  void failedPageDoesNotAdvanceCheckpointAndReplayAndTenantsRemainIsolated() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/feed",
        exchange -> {
          String cursor = exchange.getRequestURI().getQuery();
          String result =
              cursor.equals("cursor=")
                  ? "{\"changes\":[{\"id\":\"one\",\"deleted\":false,\"document\":{\"title\":\"one\",\"price\":10,\"currency\":\"EUR\",\"tenantId\":\"forged\"}}],\"nextCursor\":\"page2\",\"hasMore\":true}"
                  : cursor.equals("cursor=page2")
                      ? "{\"changes\":[{\"id\":\"two\",\"deleted\":false,\"document\":{\"title\":\"two\",\"price\":20,\"currency\":\"EUR\"}},{\"id\":\"one\",\"deleted\":true,\"document\":{}}],\"nextCursor\":\"done\",\"hasMore\":false}"
                      : "{\"changes\":[],\"nextCursor\":\"done\",\"hasMore\":false}";
          byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/feed";
    var sink = mock(OpenSearchApiClient.class);
    var documents = new ConcurrentHashMap<String, Map<String, Object>>();
    var fail = new AtomicBoolean(true);
    when(sink.upsert(anyString(), anyString(), anyMap()))
        .thenAnswer(
            call -> {
              if (fail.getAndSet(false)) throw new IllegalStateException("backend failure");
              documents.put(call.getArgument(1), call.getArgument(2));
              return Map.of();
            });
    when(sink.delete(anyString(), anyString()))
        .thenAnswer(
            call -> {
              documents.remove(call.getArgument(1));
              return Map.of();
            });
    var ai = mock(io.qwenbridge.ai.service.AIService.class);
    when(ai.embed(any()))
        .thenReturn(
            new io.qwenbridge.ai.contract.EmbeddingResponse(
                java.util.Collections.nCopies(1024, 0.1)));
    var sync =
        new CatalogSynchronization(
            sink,
            new OpenSearchProperties(null, "test-catalog", 10, null, null),
            redis,
            mapper,
            ai,
            Optional.of(endpoint),
            Optional.empty(),
            "http-json");
    String tenant = "sync-" + java.util.UUID.randomUUID();
    var caller = new ApiIdentity.Principal("alice", tenant, Set.of("catalog:write"));
    String checkpoint = "qwenbridge:catalog:" + tenant + ":" + ApiIdentity.digest(endpoint);
    try {
      assertThatThrownBy(() -> sync.synchronize(caller)).hasMessageContaining("backend failure");
      assertThat(redis.value(String.class).get(checkpoint)).isNull();
      assertThat(sync.synchronize(caller))
          .containsEntry("status", "COMPLETE")
          .containsEntry("checkpoint", "done");
      assertThat(documents).hasSize(1);
      assertThat(documents.values().iterator().next())
          .containsEntry("title", "two")
          .containsEntry("tenantId", tenant);
      assertThat(sync.synchronize(caller)).containsEntry("applied", 0);
      var other =
          new ApiIdentity.Principal(
              "bob", "other-" + java.util.UUID.randomUUID(), Set.of("catalog:write"));
      sync.synchronize(other);
      assertThat(documents).hasSize(2);
    } finally {
      server.stop(0);
    }
  }
}
