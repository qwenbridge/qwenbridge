package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;
import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@TestProfile(OpenSearchSecureIntegrationTest.Secure.class)
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_SECURE_OPENSEARCH_IT", matches = "true")
class OpenSearchSecureIntegrationTest {
  public static class Secure implements QuarkusTestProfile {
    private static final String INDEX = "qb-secure-" + java.util.UUID.randomUUID();

    public Map<String, String> getConfigOverrides() {
      if (!"true".equals(System.getenv("QWENBRIDGE_RUN_SECURE_OPENSEARCH_IT"))) return Map.of();
      return Map.of(
          "qwenbridge.search.opensearch.base-url",
          System.getenv("QWENBRIDGE_TEST_OPENSEARCH_URL"),
          "qwenbridge.search.opensearch.index",
          INDEX,
          "qwenbridge.search.opensearch.username",
          "admin",
          "qwenbridge.search.opensearch.password",
          System.getenv("QWENBRIDGE_TEST_OPENSEARCH_PASSWORD"),
          "quarkus.rest-client.opensearch.tls-configuration-name",
          "secure-test",
          "quarkus.tls.secure-test.trust-store.p12.path",
          System.getenv("QWENBRIDGE_TEST_TLS_DIR") + "/truststore.p12",
          "quarkus.tls.secure-test.trust-store.p12.password",
          "changeit");
    }
  }

  @Inject @RestClient OpenSearchApiClient api;
  @Inject OpenSearchProvider provider;
  @Inject OpenSearchProperties properties;

  @Test
  void validatesCertificatesCredentialsAndTenantFilteredExecution() throws Exception {
    var fields =
        Map.of(
            "tenantId",
            Map.of("type", "keyword"),
            "title",
            Map.of("type", "text"),
            "price",
            Map.of("type", "double"),
            "currency",
            Map.of("type", "keyword"));
    api.createIndex(properties.index(), Map.of("mappings", Map.of("properties", fields)));
    api.upsert(
        properties.index(),
        "good",
        Map.of("tenantId", "secure", "title", "shoe", "price", 99, "currency", "EUR"));
    api.upsert(
        properties.index(),
        "boundary",
        Map.of("tenantId", "secure", "title", "shoe", "price", 100, "currency", "EUR"));
    api.upsert(
        properties.index(),
        "other-tenant",
        Map.of("tenantId", "other", "title", "shoe", "price", 20, "currency", "EUR"));
    var span = new Evidence("original", 0, 1, "x");
    var plan =
        CommerceCompiler.validate(
            CommerceSchemaRegistry.defaults(),
            "secure",
            List.of(
                new Constraint("price", Operator.LT, "100", span),
                new Constraint("currency", Operator.EQ, "EUR", span)));
    // Wait for index refresh, not a fixed timing assumption.
    java.util.List<String> ids = List.of();
    for (int i = 0; i < 30; i++) {
      ids =
          provider
              .search(
                  new io.qwenbridge.execution.provider.model.SearchRequest(
                      "shoe", Map.of("commercePlan", plan), Map.of()))
              .results()
              .hits()
              .stream()
              .map(io.qwenbridge.execution.provider.model.SearchHit::id)
              .toList();
      if (ids.contains("good")) break;
      Thread.sleep(100);
    }
    assertThat(ids).containsExactly("good");
    try (var response = api.ping()) {
      assertThat(response.getStatus()).isEqualTo(200);
    }
    URI url = URI.create(System.getenv("QWENBRIDGE_TEST_OPENSEARCH_URL"));
    try (var untrusted = HttpClient.newHttpClient()) {
      assertThatThrownBy(
              () ->
                  untrusted.send(
                      HttpRequest.newBuilder(url).GET().build(),
                      HttpResponse.BodyHandlers.discarding()))
          .isInstanceOf(java.io.IOException.class);
    }
    var store = KeyStore.getInstance("PKCS12");
    try (var in =
        Files.newInputStream(Path.of(System.getenv("QWENBRIDGE_TEST_TLS_DIR"), "truststore.p12"))) {
      store.load(in, "changeit".toCharArray());
    }
    var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    managers.init(store);
    var ssl = SSLContext.getInstance("TLS");
    ssl.init(null, managers.getTrustManagers(), null);
    try (var trusted = HttpClient.newBuilder().sslContext(ssl).build()) {
      var rejected =
          trusted.send(
              HttpRequest.newBuilder(url)
                  .header("Authorization", "Basic YWRtaW46d3Jvbmc=")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      assertThat(rejected.statusCode()).isEqualTo(401);
    }
  }
}
