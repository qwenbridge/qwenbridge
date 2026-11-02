package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-scoped Commerce schema versions (QB-149). Versions are immutable and stored through the
 * {@link io.qwenbridge.platform.PlatformStore}, so they follow the configured adapter (memory or
 * Redis) instead of requiring Redis. Versions written by earlier releases under the legacy Redis
 * key are still read (never rewritten or deleted) when the Redis adapter is in use. The built-in
 * {@code commerce} version 1 is the Commerce pack's schema, which the Core schema registry holds
 * from activation.
 */
@ApplicationScoped
public final class CommerceSchemaRegistry {
  private final ConcurrentHashMap<String, Schema> schemas = new ConcurrentHashMap<>();
  private final io.qwenbridge.platform.PlatformStore store;
  private final java.util.function.Supplier<io.quarkus.redis.datasource.RedisDataSource> legacy;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  /** In-process only (unit tests). */
  public CommerceSchemaRegistry() {
    this(null, () -> null, null);
  }

  @jakarta.inject.Inject
  public CommerceSchemaRegistry(
      io.qwenbridge.platform.PlatformStore store,
      jakarta.enterprise.inject.Instance<io.quarkus.redis.datasource.RedisDataSource> redis,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this(store, redis::get, mapper);
  }

  public CommerceSchemaRegistry(
      io.qwenbridge.platform.PlatformStore store,
      java.util.function.Supplier<io.quarkus.redis.datasource.RedisDataSource> legacy,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this.store = store;
    this.legacy = legacy;
    this.mapper = mapper == null ? null : mapper.copy();
  }

  private static String id(Schema schema) {
    return schema.id() + "." + schema.version();
  }

  /** A version written by an earlier release directly to Redis, read-only. */
  private Schema legacy(String tenant, String id, int version) {
    if (store == null || !"redis".equals(store.adapter()) || mapper == null) return null;
    var redis = legacy.get();
    if (redis == null) return null;
    String raw =
        redis.value(String.class).get("qwenbridge:schema:" + tenant + ":" + id + ":" + version);
    if (raw == null) return null;
    try {
      return mapper.readValue(raw, Schema.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Invalid stored schema", ex);
    }
  }

  public Schema register(String tenant, Schema schema) {
    if (schema.id().equals("commerce") && schema.version() == 1 && !schema.equals(defaults()))
      throw new IllegalArgumentException("Built-in commerce version 1 is immutable");
    String key = tenant + ":" + schema.id() + ":" + schema.version();
    var older = legacy(tenant, schema.id(), schema.version());
    if (older != null && !older.equals(schema))
      throw new IllegalArgumentException("Schema versions are immutable; increment the version");
    if (store != null)
      store.update(
          tenant,
          "schemas",
          id(schema),
          Schema.class,
          null,
          previous -> {
            if (previous != null && !previous.equals(schema))
              throw new IllegalArgumentException(
                  "Schema versions are immutable; increment the version");
            return previous == null ? schema : previous;
          });
    Schema previous = schemas.putIfAbsent(key, schema);
    if (previous != null && !previous.equals(schema))
      throw new IllegalArgumentException("Schema versions are immutable; increment the version");
    return schema;
  }

  public Schema get(String tenant, String id, int version) {
    if ("commerce".equals(id) && version == 1) return defaults();
    Schema schema = schemas.get(tenant + ":" + id + ":" + version);
    if (schema == null && store != null && id.matches("[a-z][a-z0-9-]{0,63}"))
      schema = store.read(tenant, "schemas", id + "." + version, Schema.class);
    if (schema == null) schema = legacy(tenant, id, version);
    if (schema == null) throw new IllegalArgumentException("Unknown schema version");
    return schema;
  }

  public static Schema defaults() {
    // The installed Commerce module owns the field contract; the legacy public v1 schema
    // omits the internal domain discriminator and keeps ISO currency uppercase.
    Schema pack =
        io.qwenbridge.platform.DomainExecution.schema(
            new io.qwenbridge.packs.CommercePack().definition());
    var fields = new java.util.HashMap<>(pack.fields());
    fields.remove("domain");
    fields.put(
        "currency",
        new Field(ValueType.TEXT, Set.of(Operator.EQ), "currency", Normalization.UPPERCASE));
    return new Schema(pack.id(), pack.version(), fields);
  }
}
