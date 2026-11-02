import io.qwenbridge.sdk.commerce.CommerceClient;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import java.net.URI;
import java.util.List;

/**
 * QB-286: the Java SDK reads the multilingual expansion from the running server (database-free,
 * eight configured identifiers). Exits non-zero on any contract violation.
 */
public class JavaSdkLiveExpansion {
  public static void main(String[] args) {
    String baseUrl = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    try (var client = new CommerceClient(URI.create(baseUrl), "")) {
      var response =
          client.search(new CommerceContracts.Request("I need a red winter jacket under 100 euros"));
      var e = response.expansion();
      check(e != null, "no expansion in the response (status " + response.status() + ")");
      check(
          e.requestedLanguages().equals(List.of("en", "sv", "fa", "nb", "nn", "de", "ja", "DK")),
          "requested " + e.requestedLanguages());
      check(e.targets().size() == 8, "targets " + e.targets().size());
      check(
          "jag behöver en röd vinterjacka under 100 euro"
              .equals(e.target("sv").orElseThrow().text()),
          "sv text");
      check("UNCHANGED".equals(e.target("en").orElseThrow().status()), "en status");
      System.out.println(
          "java-sdk-live-expansion ok status="
              + e.status()
              + " successful="
              + e.successfulLanguages());
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      System.err.println("java-sdk-live-expansion FAILED: " + message);
      System.exit(1);
    }
  }
}
