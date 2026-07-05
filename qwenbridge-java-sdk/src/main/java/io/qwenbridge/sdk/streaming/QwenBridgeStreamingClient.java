package io.qwenbridge.sdk.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class QwenBridgeStreamingClient {

  private final QwenBridgeClientConfig config;
  private final HttpClient httpClient;
  private final StreamingPayloadMapper payloadMapper;

  public QwenBridgeStreamingClient(QwenBridgeClientConfig config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.httpClient = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    this.payloadMapper =
        new StreamingPayloadMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  public CompletableFuture<Void> stream(String requestId, StreamingEventHandler handler) {
    validateRequestId(requestId);
    Objects.requireNonNull(handler, "handler must not be null");

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(streamEndpoint(requestId))
            .timeout(config.requestTimeout())
            .header("Accept", "text/event-stream")
            .GET()
            .build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenAccept(
            response -> {
              if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new QwenBridgeTransportException(
                    "Failed to open QwenBridge SSE stream: HTTP " + response.statusCode(), null);
              }

              parseEvents(response.body(), handler);
            });
  }

  public CompletableFuture<Void> streamTyped(String requestId, TypedStreamingEventHandler handler) {
    Objects.requireNonNull(handler, "handler must not be null");

    return stream(requestId, event -> handler.onEvent(payloadMapper.map(event)));
  }

  private void parseEvents(String body, StreamingEventHandler handler) {
    try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
      String event = null;
      StringBuilder data = new StringBuilder();

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          emitIfComplete(event, data, handler);
          event = null;
          data.setLength(0);
          continue;
        }

        if (line.startsWith("event:")) {
          event = line.substring("event:".length()).trim();
          continue;
        }

        if (line.startsWith("data:")) {
          if (!data.isEmpty()) {
            data.append('\n');
          }
          data.append(line.substring("data:".length()).trim());
        }
      }

      emitIfComplete(event, data, handler);
    } catch (IOException e) {
      throw new QwenBridgeTransportException("Failed to parse QwenBridge SSE stream", e);
    }
  }

  private void emitIfComplete(String event, StringBuilder data, StreamingEventHandler handler) {
    if (event != null && !data.isEmpty()) {
      handler.onEvent(new StreamingEvent(event, data.toString()));
    }
  }

  private URI streamEndpoint(String requestId) {
    String encodedRequestId = URLEncoder.encode(requestId, StandardCharsets.UTF_8);

    return config.baseUrl().resolve("/api/v1/search/stream/" + encodedRequestId);
  }

  private void validateRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }
  }
}
