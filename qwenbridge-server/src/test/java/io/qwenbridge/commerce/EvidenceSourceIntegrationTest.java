package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_REDIS_IT", matches = "true")
class EvidenceSourceIntegrationTest {
  @Inject CommerceEvidenceStore store;
  @Inject io.quarkus.redis.datasource.RedisDataSource redis;
  @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;

  @Test
  void schemaVersionsSurviveRegistryRecreationAndRemainTenantScoped() {
    String tenant = "registry-" + java.util.UUID.randomUUID();
    var first =
        new CommerceSchemaRegistry(
            new io.qwenbridge.platform.PlatformStore(redis, mapper, java.util.Optional.of(true)),
            () -> redis,
            mapper);
    var second =
        new CommerceSchemaRegistry(
            new io.qwenbridge.platform.PlatformStore(redis, mapper, java.util.Optional.of(true)),
            () -> redis,
            mapper);
    var v2 = new CommerceContracts.Schema("custom", 2, CommerceSchemaRegistry.defaults().fields());
    first.register(tenant, v2);
    assertThat(second.get(tenant, "custom", 2)).isEqualTo(v2);
    assertThatThrownBy(() -> second.get("other-tenant", "custom", 2))
        .hasMessageContaining("Unknown");
    assertThatThrownBy(
            () ->
                second.register(
                    tenant,
                    new CommerceContracts.Schema(
                        "custom", 2, java.util.Map.of("price", v2.fields().get("price")))))
        .hasMessageContaining("immutable");
  }

  @Test
  void storesRealSourceTimeAndContentWithoutCrossTenantAccess() {
    String tenant = "evidence-test-" + java.util.UUID.randomUUID();
    String source = "pool rejected request at queue limit 24";
    var span =
        new io.qwenbridge.analysis.model.Grounded.Evidence(
            "machine-event", 0, source.length(), source);
    Instant occurred = Instant.parse("2026-10-31T00:00:00Z");
    var item =
        store.recordSource(
            tenant,
            "urn:redis:pool:event-1",
            source,
            "Queue limit was reached",
            occurred,
            List.of(span),
            null);
    assertThat(store.get(tenant, item.id())).isEqualTo(item);
    assertThat(store.sourceText(tenant, item.id())).isEqualTo(source);
    assertThat(item.occurredAt()).isEqualTo(occurred);
    assertThat(item.observedAt()).isAfterOrEqualTo(occurred);
    assertThat(store.get("different-tenant", item.id())).isNull();
    assertThatThrownBy(
            () ->
                store.recordSource(
                    tenant,
                    "urn:test",
                    source,
                    "claim",
                    occurred,
                    List.of(
                        new io.qwenbridge.analysis.model.Grounded.Evidence(
                            "machine-event", 0, 5, "wrong")),
                    null))
        .hasMessageContaining("match");
  }
}
