package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.*;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.commerce.*;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.platform.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;

@QuarkusTest
@TestProfile(CommerceBackendFlowIntegrationTest.Live.class)
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
/**
 * Natural language compiles into grounded constraints and executes on a backend with no manual
 * filters: the matching document is returned, an over-budget one and another tenant's copy are not.
 * QB-287: formerly run against the removed Elasticsearch, PostgreSQL and MongoDB connectors behind
 * the removed table-1 step; now against the default in-process backend with the live model.
 */
class CommerceBackendFlowIntegrationTest {
  /**
   * Deterministic test-only issuer shared across Quarkus test classloaders. Never production trust.
   */
  static KeyPair testIssuer() {
    try {
      var random = SecureRandom.getInstance("SHA1PRNG");
      random.setSeed(
          "QwenBridge acceptance fixture ONLY; not a production issuer"
              .getBytes(StandardCharsets.UTF_8));
      var generator = KeyPairGenerator.getInstance("Ed25519");
      generator.initialize(NamedParameterSpec.ED25519, random);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static class Live implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.licensing.trusted-keys",
          "{\"backend-flow-test\":\""
              + Base64.getEncoder().encodeToString(testIssuer().getPublic().getEncoded())
              + "\"}",
          "qwenbridge.analysis.cache.analysis-timeout",
          "60s",
          "qwenbridge.analysis.cache.enabled",
          "true",
          "qwenbridge.analysis.cache.key-prefix",
          "qwenbridge:verification:backend-flow-" + UUID.randomUUID());
    }
  }

  @Inject ObjectMapper json;
  @Inject Entitlements entitlements;
  @Inject CommerceBackends backends;
  @Inject CommerceSearchService search;

  @Test
  void naturalLanguageCompilesAndExecutesOnTheInProcessBackendWithoutManualFilters()
      throws Exception {
    String tenant = "backendFlow";
    var caller =
        new ApiIdentity.Principal("integration", tenant, Set.of("search", "license:write"));
    byte[] payload =
        json.writeValueAsBytes(
            new Entitlements.License(
                1,
                "flow-test",
                "backend-flow-test",
                tenant,
                Entitlements.Edition.ENTERPRISE,
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(1800),
                Entitlements.MATRIX.get(Entitlements.Edition.ENTERPRISE)));
    var signer = Signature.getInstance("Ed25519");
    signer.initSign(testIssuer().getPrivate());
    signer.update(payload);
    entitlements.install(
        caller,
        new Entitlements.SignedLicense(
            "backend-flow-test",
            Base64.getEncoder().encodeToString(payload),
            Base64.getEncoder().encodeToString(signer.sign())));
    List<String> queries =
        List.of(
            "black Nike shoes size 42 under 100 EUR not refurbished in stock",
            "काले Nike जूते साइज़ 42, 100 EUR से कम, refurbished नहीं, स्टॉक में");
    List<Map<String, Object>> evidence = new ArrayList<>();
    for (String id : List.of(MemoryCommerceBackend.ID)) {
      var backend = backends.backend(tenant, id);
      backend.initialize();
      var doc =
          new HashMap<String, Object>(
              Map.of(
                  "title",
                  "Nike black shoes",
                  "brand",
                  "Nike",
                  "category",
                  "shoes",
                  "color",
                  "black",
                  "size",
                  "42",
                  "price",
                  99,
                  "currency",
                  "EUR",
                  "condition",
                  "new",
                  "inStock",
                  true));
      doc.put("domain", "commerce");
      backend.upsert(tenant, "flow-good", doc);
      var wrong = new HashMap<>(doc);
      wrong.put("price", 100);
      backend.upsert(tenant, "flow-over-budget", wrong);
      var foreign = new HashMap<>(doc);
      backend.upsert("foreignFlow", "flow-good", foreign);
      try {
        for (String query : queries) {
          var response =
              search.search(
                  new SearchRequest(
                      null,
                      query,
                      "en",
                      "commerce",
                      1,
                      "KEYWORD",
                      List.of(),
                      false,
                      id,
                      10,
                      null,
                      List.of()),
                  caller);
          assertThat(response.status()).as(id + ":" + query).isEqualTo("OK");
          assertThat(response.results().hits())
              .extracting(h -> h.id())
              .containsExactly("flow-good");
          assertThat(response.queryIR().constraints()).hasSizeGreaterThanOrEqualTo(7);
          evidence.add(
              Map.of(
                  "backend",
                  id,
                  "query",
                  query,
                  "status",
                  response.status(),
                  "ids",
                  response.results().hits().stream().map(h -> h.id()).toList(),
                  "constraints",
                  response.queryIR().constraints(),
                  "timings",
                  response.timings()));
        }
      } finally {
        backend.delete(tenant, "flow-good");
        backend.delete(tenant, "flow-over-budget");
        backend.delete("foreignFlow", "flow-good");
      }
    }
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/multilingual-backend-flow.json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
  }
}
