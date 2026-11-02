package io.qwenbridge.ai.provider.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.*;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.value.ProviderId;
import io.qwenbridge.plugin.QwenBridgePlugin;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Vendor-neutral gateway protocol, documented independently of Ollama's wire format. */
@Singleton
public final class JsonHttpProvider implements AIProvider {
  private final ObjectMapper mapper;
  private final String base;
  private final String token;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final java.util.concurrent.ExecutorService workers =
      Executors.newVirtualThreadPerTaskExecutor();

  @jakarta.inject.Inject
  public JsonHttpProvider(
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.ai.http-json.base-url") java.util.Optional<String> base,
      @ConfigProperty(name = "qwenbridge.ai.http-json.token") java.util.Optional<String> token) {
    this(mapper, base.orElse(""), token.orElse(""));
  }

  public JsonHttpProvider(ObjectMapper mapper, String base, String token) {
    this.mapper = mapper.copy();
    this.base = base;
    this.token = token;
    if (!base.isBlank() && !URI.create(base).getScheme().matches("https?"))
      throw new IllegalArgumentException("Invalid AI gateway URI");
  }

  public ProviderId providerId() {
    return new ProviderId("http-json");
  }

  public QwenBridgePlugin.Manifest manifest() {
    return new QwenBridgePlugin.Manifest(
        "http-json", "1.0.0", 1, Set.of("chat", "stream", "embedding", "structured-output"));
  }

  public boolean configured() {
    return !base.isBlank();
  }

  public ChatResponse chat(ChatRequest request) {
    var payload = new java.util.HashMap<String, Object>();
    payload.put("prompt", request.prompt());
    if (request.outputSchema() != null) payload.put("outputSchema", request.outputSchema());
    var response = send("/chat", payload);
    if (!response.path("content").isTextual()) throw new AIException("Gateway content missing");
    return new ChatResponse(response.get("content").asText());
  }

  public EmbeddingResponse embed(EmbeddingRequest request) {
    var response = send("/embeddings", Map.of("text", request.text()));
    if (!response.path("vector").isArray() || response.get("vector").isEmpty())
      throw new AIException("Gateway vector missing");
    List<Double> vector = new java.util.ArrayList<>();
    response
        .get("vector")
        .forEach(
            n -> {
              if (!n.isNumber() || !Double.isFinite(n.asDouble()))
                throw new AIException("Invalid gateway vector");
              vector.add(n.asDouble());
            });
    return new EmbeddingResponse(vector);
  }

  public Multi<StreamingChatChunk> streamChat(StreamingChatRequest request) {
    return Multi.createFrom()
        .emitter(
            emitter -> {
              var body =
                  new java.util.concurrent.atomic.AtomicReference<
                      java.util.stream.Stream<String>>();
              var task =
                  workers.submit(
                      () -> {
                        try {
                          var payload = new java.util.HashMap<String, Object>();
                          payload.put("prompt", request.prompt());
                          if (request.outputSchema() != null)
                            payload.put("outputSchema", request.outputSchema());
                          var response =
                              client.send(
                                  request("/stream", payload), HttpResponse.BodyHandlers.ofLines());
                          body.set(response.body());
                          try (var lines = response.body()) {
                            if (response.statusCode() != 200)
                              throw new AIException("Gateway stream HTTP " + response.statusCode());
                            var iterator = lines.iterator();
                            boolean done = false;
                            while (!emitter.isCancelled() && iterator.hasNext()) {
                              String line = iterator.next();
                              if (line.isBlank()) continue;
                              if (line.length() > 65536)
                                throw new AIException("Gateway stream chunk too large");
                              var node = mapper.readTree(line);
                              if (!node.path("content").isTextual()
                                  || !node.path("done").isBoolean())
                                throw new AIException("Invalid gateway chunk");
                              done = node.get("done").asBoolean();
                              emitter.emit(
                                  new StreamingChatChunk(node.get("content").asText(), done));
                              if (done) break;
                            }
                            if (!done && !emitter.isCancelled())
                              throw new AIException("Gateway stream ended without terminal chunk");
                            emitter.complete();
                          }
                        } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                          emitter.fail(new AIException("Gateway stream interrupted", ex));
                        } catch (Exception ex) {
                          emitter.fail(ex);
                        }
                      });
              emitter.onTermination(
                  () -> {
                    task.cancel(true);
                    var stream = body.get();
                    if (stream != null) stream.close();
                  });
            });
  }

  private HttpRequest request(String path, Object payload) {
    if (base.isBlank()) throw new AIException("HTTP JSON provider is not configured");
    try {
      var builder =
          HttpRequest.newBuilder(URI.create(base.replaceAll("/$", "") + path))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json");
      if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
      return builder
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
          .build();
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new AIException("Cannot encode gateway request", ex);
    }
  }

  private com.fasterxml.jackson.databind.JsonNode send(String path, Object payload) {
    try {
      var response = client.send(request(path, payload), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || response.body().length() > 1_048_576)
        throw new AIException("Gateway HTTP " + response.statusCode());
      return mapper.readTree(response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AIException("Gateway interrupted", ex);
    } catch (java.io.IOException ex) {
      throw new AIException("Gateway transport failed", ex);
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
    client.close();
  }
}
