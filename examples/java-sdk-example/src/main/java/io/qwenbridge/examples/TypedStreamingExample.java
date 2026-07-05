package io.qwenbridge.examples;

import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient;
import io.qwenbridge.sdk.streaming.TypedStreamingEvent;
import io.qwenbridge.sdk.streaming.payload.AICompletedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AIFailedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AITokenStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.ConnectedStreamingPayload;
import java.net.URI;
import java.time.Duration;

public class TypedStreamingExample {

  public static void main(String[] args) {
    QwenBridgeStreamingClient client =
        new QwenBridgeStreamingClient(
            new QwenBridgeClientConfig(
                URI.create("http://localhost:8080"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)));

    String requestId = args.length > 0 ? args[0] : "demo-request-id";

    client.streamTyped(requestId, TypedStreamingExample::handleEvent).join();
  }

  private static void handleEvent(TypedStreamingEvent event) {
    switch (event.payload()) {
      case ConnectedStreamingPayload payload ->
          System.out.println("connected sessionId=" + payload.sessionId());

      case AITokenStreamingPayload payload -> System.out.print(payload.content());

      case AICompletedStreamingPayload payload ->
          System.out.println("\ncompleted tokenCount=" + payload.tokenCount());

      case AIFailedStreamingPayload payload ->
          System.err.println("failed code=" + payload.code() + " message=" + payload.message());

      default -> System.out.println("event=" + event.eventName() + " raw=" + event.rawData());
    }
  }
}
