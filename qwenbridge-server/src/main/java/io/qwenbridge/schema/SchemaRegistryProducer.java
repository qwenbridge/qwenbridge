package io.qwenbridge.schema;

import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.domains.DomainPacks;
import io.qwenbridge.pack.DomainPack;
import io.qwenbridge.pack.QwenBridgePack;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Provides the application's in-memory {@link SchemaRegistry} (QB-043, QB-047, QB-049) and the
 * {@link DomainPacks} that own its domain schemas (QB-232 – QB-236). It needs no database, Redis or
 * network. Domain packs on the classpath are activated at startup under their own owner id ({@code
 * pack:<id>}); a pack that cannot be loaded or conflicts with another is skipped and logged without
 * affecting the Core or other packs. {@code qwenbridge.domains.disabled} lists pack ids not to
 * activate.
 *
 * <p>The bundled Commerce pack is registered here like any other pack (QB-149). Tenant-specific
 * Commerce schema versions ({@code CommerceSchemaRegistry}) are stored through the platform store
 * adapter and are not global, so they are not registered here.
 */
public class SchemaRegistryProducer {

  private static final Logger LOG = Logger.getLogger(SchemaRegistryProducer.class);

  @Produces
  @Singleton
  DomainPacks domainPacks() {
    List<String> disabled =
        ConfigProvider.getConfig()
            .getOptionalValues("qwenbridge.domains.disabled", String.class)
            .orElse(List.of());
    return domainPacks(disabled);
  }

  static DomainPacks domainPacks(List<String> disabled) {
    var packs = new DomainPacks(new SchemaRegistry());
    List<DomainPack> found = new ArrayList<>();
    ServiceLoader.load(QwenBridgePack.class, SchemaRegistryProducer.class.getClassLoader()).stream()
        .forEach(
            provider -> {
              try {
                if (provider.get() instanceof DomainPack pack
                    && !disabled.contains(pack.manifest().id())) found.add(pack);
              } catch (RuntimeException | java.util.ServiceConfigurationError e) {
                LOG.warnf(
                    "Skipped domain pack %s: %s",
                    provider.type().getName(), e.getClass().getSimpleName());
              }
            });
    packs
        .activateAll(found)
        .forEach((id, reason) -> LOG.warnf("Skipped domain pack %s: %s", id, reason));
    LOG.infof(
        "Schema registry ready with %d schema(s) from %d domain pack(s)%s",
        packs.schemas().keys().size(),
        packs.registrations().size(),
        disabled.isEmpty() ? "" : "; disabled: " + disabled);
    return packs;
  }

  @Produces
  @Singleton
  SchemaRegistry schemaRegistry(DomainPacks packs) {
    return packs.schemas();
  }

  /** The registry with every classpath pack active (tests and tools). */
  SchemaRegistry schemaRegistry() {
    return domainPacks(List.of()).schemas();
  }
}
