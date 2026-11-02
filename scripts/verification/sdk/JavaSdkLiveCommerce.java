import io.qwenbridge.sdk.commerce.CommerceClient;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Section 3 gate (QB-186, QB-188, QB-191): the Java SDK's v2 client against the running server.
 * Version compatibility, a Unicode search, a stream with the 1.0 event fields, a typed validation
 * error and cancellation. Exits non-zero on any contract violation.
 */
public class JavaSdkLiveCommerce {
  public static void main(String[] args) {
    String baseUrl = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    String token = System.getenv().getOrDefault("QWENBRIDGE_TEST_API_TOKEN", "");
    try (var client = new CommerceClient(URI.create(baseUrl), token)) {
      var version = client.requireCompatibleServer();
      check(version.apiVersions().contains("v1"), "v1 not listed");

      String id = "sdk-live-" + UUID.randomUUID();
      var request =
          new CommerceContracts.Request(
                  "black Nike shoes size 42 under 100 EUR, not refurbished, in stock")
              .withRequestId(id);
      var response = client.search(request);
      check(response.apiVersion() == 2, "apiVersion");
      check(id.equals(response.requestId()), "request id not echoed");
      check("OK".equals(response.status()), "status " + response.status());

      var unicode = client.search(new CommerceContracts.Request("کفش مشکی Nike سایز ۴۲"));
      check(unicode.status() != null, "unicode search had no status");

      var events = new ArrayList<CommerceContracts.Event>();
      client.stream(new CommerceContracts.Request("black Nike shoes size 42"), events::add);
      check(events.size() == 2, "expected started + terminal, got " + events.size());
      check(events.get(0).sequence() == 1 && events.get(1).sequence() == 2, "sequence");
      check(Boolean.TRUE.equals(events.get(1).terminal()), "terminal flag");
      check(events.get(1).traceId() != null && events.get(1).traceId().length() == 32, "traceId");

      try {
        client.search(new CommerceContracts.Request("bad id", "shoes", "en", "commerce", 1, "KEYWORD", false));
        check(false, "an invalid request ID was accepted");
      } catch (QwenBridgeApiException e) {
        check(e.status() == 400 && "VALIDATION".equals(e.category()), "error " + e.status() + " " + e.category());
      }
      check(!client.cancel("sdk-live-not-in-flight"), "cancelled an unknown request");
      System.out.println(
          "java-sdk-live-commerce ok server=" + version.version() + " events=" + events.size());
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      System.err.println("java-sdk-live-commerce FAILED: " + message);
      System.exit(1);
    }
  }
}
