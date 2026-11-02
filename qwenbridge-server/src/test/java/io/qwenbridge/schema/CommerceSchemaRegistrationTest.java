package io.qwenbridge.schema;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.commerce.CommerceContracts.Schema;
import io.qwenbridge.commerce.CommerceSchemaRegistry;
import io.qwenbridge.platform.PlatformStore;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * QB-149: the Commerce schema is the pack's schema in the Core registry, and tenant schema versions
 * persist through the platform store adapter (no direct Redis).
 */
class CommerceSchemaRegistrationTest {

  @Test
  void commerceIsRegisteredInTheCoreRegistryByItsPack() {
    var registry = new SchemaRegistryProducer().schemaRegistry();
    assertThat(registry.owner("commerce")).contains("pack:commerce");
    var core = registry.latest("commerce").orElseThrow();
    var pack = DomainPackSchemas.toSchema(new io.qwenbridge.packs.CommercePack().definition());
    assertThat(core).isEqualTo(pack);
    // The apiVersion 2 view is the same pack schema without the internal domain discriminator.
    var v2 = CommerceSchemaRegistry.defaults();
    var expected = new java.util.TreeSet<>(core.fields().keySet());
    expected.remove("domain");
    assertThat(new java.util.TreeSet<>(v2.fields().keySet())).isEqualTo(expected);
  }

  @Test
  void tenantSchemaVersionsPersistThroughTheStoreAdapterAndStayImmutable() {
    var store =
        new PlatformStore(null, new ObjectMapper().findAndRegisterModules(), Optional.of(false));
    assertThat(store.adapter()).isEqualTo("memory");
    var first = new CommerceSchemaRegistry(store, () -> null, new ObjectMapper());
    var second = new CommerceSchemaRegistry(store, () -> null, new ObjectMapper());
    var v2 = new Schema("custom", 2, CommerceSchemaRegistry.defaults().fields());
    first.register("shopA", v2);
    assertThat(second.get("shopA", "custom", 2)).isEqualTo(v2);
    assertThatThrownBy(() -> second.get("shopB", "custom", 2)).hasMessageContaining("Unknown");
    assertThatThrownBy(
            () ->
                second.register(
                    "shopA", new Schema("custom", 2, Map.of("price", v2.fields().get("price")))))
        .hasMessageContaining("immutable");
    assertThat(second.register("shopA", v2)).isEqualTo(v2);
  }
}
