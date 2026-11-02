package io.qwenbridge.ai.routing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-246 – QB-248 over HTTP: model and capability discovery, with a configured fallback, in an
 * authenticated deployment. Configuring a fallback opens nothing: discovery still needs a
 * principal, and the tenant-less v1 chat stays refused. No provider is called.
 */
@QuarkusTest
@TestProfile(AiModelsApiTest.WithFallback.class)
class AiModelsApiTest {

  static final String KEY = "test-only-ai-models-key-1234567890";

  public static class WithFallback implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.security.authentication-required", "true",
          "qwenbridge.security.api-principals",
              "[{\"key\":\""
                  + KEY
                  + "\",\"subject\":\"ana\",\"tenant\":\"acme\",\"roles\":[\"search\"]}]",
          "qwenbridge.ai.fallback-providers", "http-json",
          "qwenbridge.ai.http-json.base-url", "http://127.0.0.1:9/gateway",
          "qwenbridge.ai.http-json.token", "test-only-gateway-secret-token");
    }
  }

  @Test
  void modelsAndCapabilitiesAreDiscoverableWithoutCredentials() {
    given()
        .header("Authorization", "Bearer " + KEY)
        .get("/api/v2/ai/models")
        .then()
        .statusCode(200)
        .body("chat.primary", equalTo("ollama"))
        .body("chat.fallbacks", hasItems("http-json"))
        .body("chat.fallbackPolicy", equalTo("ON_PROVIDER_FAULT"))
        .body("chat.providers[0].model.provider", equalTo("ollama"))
        .body("chat.providers[0].capabilities.structuredOutput", equalTo(true))
        .body("chat.providers[1].model.model", equalTo("gateway"))
        .body("embedding.reindexRequiredWhen", hasItems("model", "version", "dimensions"))
        .body(not(containsString("test-only-gateway-secret-token")))
        .body(not(containsString("127.0.0.1:9")));
  }

  @Test
  void switchingProvidersDoesNotBypassAuthorization() {
    given().get("/api/v2/ai/models").then().statusCode(401);
    given()
        .header("Authorization", "Bearer " + KEY)
        .contentType("application/json")
        .body(Map.of("prompt", "hello"))
        .post("/api/v1/ai/chat")
        .then()
        .statusCode(409);
  }
}
