package io.qwenbridge.e2e;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * QB-205: every external service unreachable (port 9 refuses connections), every store in process,
 * and the model replaced by a controlled local provider. Optional connectors are switched off
 * explicitly, so the environment of a verification run (which configures Table 1 backends) cannot
 * leak in.
 */
public class ServiceFreeProfile implements QuarkusTestProfile {
  static final String KEY = "test-only-service-free-key-123456";
  static final String OTHER = "test-only-service-free-other-1234";

  @Override
  public Map<String, String> getConfigOverrides() {
    var c = new HashMap<String, String>();
    c.put("quarkus.redis.hosts", "redis://127.0.0.1:9");
    // The only network peer: the controlled local model on 127.0.0.1 (LocalModelStub).
    c.put("qwenbridge.ai.ollama.base-url", LocalModelStub.start());
    c.put("qwenbridge.search.opensearch.base-url", "http://127.0.0.1:9");
    c.put("qwenbridge.platform.store", "memory");
    c.put("qwenbridge.commerce.backend", "memory");
    c.put("qwenbridge.embedding.enabled", "false");
    c.put("qwenbridge.analysis.cache.enabled", "false");
    c.put("qwenbridge.abuse.protection.redis.enabled", "false");
    c.put("qwenbridge.demo.datasets", "commerce-demo");
    c.put("qwenbridge.demo.tenant", "shopA");
    c.put("qwenbridge.security.authentication-required", "true");
    c.put(
        "qwenbridge.security.api-principals",
        "[{\"key\":\""
            + KEY
            + "\",\"subject\":\"alice\",\"tenant\":\"shopA\",\"roles\":[\"search\"]},"
            + "{\"key\":\""
            + OTHER
            + "\",\"subject\":\"bob\",\"tenant\":\"shopB\",\"roles\":[\"search\"]}]");
    return c;
  }
}
