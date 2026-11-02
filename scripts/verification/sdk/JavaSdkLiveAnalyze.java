import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Section 1 gate: a live analyze call through the published Java SDK against the running server.
 * Exits non-zero when the call fails or the response breaks the SDK contract.
 */
public class JavaSdkLiveAnalyze {
  public static void main(String[] args) {
    String baseUrl = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    String requestId = "sdk-gate-" + UUID.randomUUID();
    QwenBridgeClient client =
        new QwenBridgeClient(
            new QwenBridgeClientConfig(
                URI.create(baseUrl), Duration.ofSeconds(5), Duration.ofSeconds(90)));
    SearchAnalyzeResponse response =
        client.analyze(
            SearchAnalyzeRequest.withRequestId(requestId, "black running shoes under 100 EUR"));
    check(requestId.equals(response.requestId()), "request id was not echoed");
    check(response.decision() != null, "decision missing");
    check(response.intent() != null, "intent missing");
    check(
        Double.isFinite(response.confidence())
            && response.confidence() >= 0
            && response.confidence() <= 1,
        "confidence outside [0,1]");
    System.out.println(
        "java-sdk-live-analyze ok requestId="
            + response.requestId()
            + " intent="
            + response.intent()
            + " decision="
            + response.decision());
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      System.err.println("java-sdk-live-analyze FAILED: " + message);
      System.exit(1);
    }
  }
}
