package io.qwenbridge.examples;

import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public class SyncSearchAnalyzeExample {

  public static void main(String[] args) {
    QwenBridgeClient client =
        new QwenBridgeClient(
            new QwenBridgeClientConfig(
                URI.create("http://localhost:8080"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)));

    SearchAnalyzeResponse response =
        client.analyze(
            SearchAnalyzeRequest.withRequestId(
                UUID.randomUUID().toString(), "best mechanical keyboard"));

    System.out.println("requestId=" + response.requestId());
    System.out.println("intent=" + response.intent());
    System.out.println("decision=" + response.decision());
    System.out.println("confidence=" + response.confidence());
  }
}
