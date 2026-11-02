package io.qwenbridge.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tenant-keyed atomic JSON storage with three adapters (QB-142, QB-254):
 *
 * <ul>
 *   <li><b>memory</b>: in-process, bounded by entry count and value size; no external service.
 *   <li><b>file</b>: one JSON file per entry under {@code qwenbridge.platform.file-dir}, written
 *       atomically (temporary file and rename); survives restarts with no database or network. For
 *       a single server instance: instances must not share the directory.
 *   <li><b>redis</b>: shared across instances, compare-and-set by a Lua script.
 * </ul>
 *
 * {@code qwenbridge.platform.store} selects the adapter. Without it, the earlier switch {@code
 * qwenbridge.platform.persistent} decides; when neither is set the memory adapter is used (QB-293:
 * Redis is an optional cache, never required for platform state) and a warning says that state is
 * lost on restart. Use {@code file} for durable state without any external service. The Redis
 * client is resolved only when the Redis adapter is selected, so a memory deployment never contacts
 * Redis from here. Every value is limited to {@code qwenbridge.platform.max-value-bytes} (QB-143).
 */
@ApplicationScoped
public final class PlatformStore {
  private record Entry(String value, Instant expires) {}

  private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();
  private final String fileDir; // a real path, as text: the store stays immutable
  private final java.util.function.Supplier<RedisDataSource> redisSource;
  private final ObjectMapper mapper;
  private final boolean persistent;
  private final int maxValueBytes;

  public PlatformStore(RedisDataSource redis, ObjectMapper mapper, Optional<Boolean> persistent) {
    this(() -> redis, mapper, persistent.orElse(false), 1_048_576);
  }

  public PlatformStore(
      jakarta.enterprise.inject.Instance<RedisDataSource> redis,
      ObjectMapper mapper,
      Optional<String> store,
      Optional<Boolean> persistent,
      int maxValueBytes) {
    this(redis, mapper, store, persistent, maxValueBytes, Optional.empty());
  }

  /** A file-backed store (QB-254, QB-259): local, offline, persistent across restarts. */
  public static PlatformStore file(ObjectMapper mapper, java.nio.file.Path directory) {
    return new PlatformStore(() -> null, mapper, false, 1_048_576, directory);
  }

  @jakarta.inject.Inject
  public PlatformStore(
      jakarta.enterprise.inject.Instance<RedisDataSource> redis,
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.platform.store") Optional<String> store,
      @ConfigProperty(name = "qwenbridge.platform.persistent") Optional<Boolean> persistent,
      @ConfigProperty(name = "qwenbridge.platform.max-value-bytes", defaultValue = "1048576")
          int maxValueBytes,
      @ConfigProperty(name = "qwenbridge.platform.file-dir") Optional<String> fileDir) {
    this(
        redis::get,
        mapper,
        store
            .map(
                s ->
                    switch (s) {
                      case "redis" -> true;
                      case "memory", "file" -> false;
                      default ->
                          throw new IllegalArgumentException(
                              "qwenbridge.platform.store must be redis or memory (or file, with"
                                  + " qwenbridge.platform.file-dir)");
                    })
            .orElseGet(() -> persistent.orElse(false)),
        maxValueBytes,
        store
            .filter("file"::equals)
            .map(
                s ->
                    java.nio.file.Path.of(
                        fileDir.orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "qwenbridge.platform.store=file requires"
                                        + " qwenbridge.platform.file-dir"))))
            .orElse(null));
  }

  private PlatformStore(
      java.util.function.Supplier<RedisDataSource> redis,
      ObjectMapper mapper,
      boolean persistent,
      int maxValueBytes) {
    this(redis, mapper, persistent, maxValueBytes, null);
  }

  private PlatformStore(
      java.util.function.Supplier<RedisDataSource> redis,
      ObjectMapper mapper,
      boolean persistent,
      int maxValueBytes,
      java.nio.file.Path fileDir) {
    if (maxValueBytes < 1024 || maxValueBytes > 16 * 1_048_576)
      throw new IllegalArgumentException("qwenbridge.platform.max-value-bytes out of range");
    this.redisSource = redis;
    this.mapper = mapper.copy();
    this.persistent = persistent;
    this.maxValueBytes = maxValueBytes;
    if (fileDir != null) {
      try {
        java.nio.file.Files.createDirectories(fileDir);
        this.fileDir = fileDir.toRealPath().toString();
      } catch (java.io.IOException e) {
        throw new IllegalArgumentException("qwenbridge.platform.file-dir is not usable", e);
      }
    } else {
      this.fileDir = null;
    }
    if (!persistent
        && this.fileDir == null
        && io.quarkus.runtime.LaunchMode.current() != io.quarkus.runtime.LaunchMode.TEST)
      org.jboss.logging.Logger.getLogger(PlatformStore.class)
          .warn(
              "Platform state (licences, packs, sessions) is kept in memory and lost on restart;"
                  + " set qwenbridge.platform.store=file with qwenbridge.platform.file-dir to keep"
                  + " it without any external service.");
  }

  private record FileEntry(String value, Long expiresAt) {}

  private java.nio.file.Path file(String key) {
    // The key's parts are validated to [A-Za-z0-9._-]; the resolved file must also be a direct
    // child of the state directory, so no key can name a path outside it.
    var dir = java.nio.file.Path.of(fileDir);
    String name = key.substring("qwenbridge:platform:".length()).replace(':', '~') + ".json";
    if (name.contains("/") || name.contains("\\") || name.startsWith("."))
      throw new IllegalArgumentException("Invalid state identity");
    var path = dir.resolve(name).normalize();
    if (!path.startsWith(dir) || !dir.equals(path.getParent()))
      throw new IllegalArgumentException("Invalid state identity");
    return path;
  }

  private String loadFile(String key) {
    var path = file(key);
    try {
      if (!java.nio.file.Files.exists(path)) return null;
      var entry = mapper.readValue(path.toFile(), FileEntry.class);
      if (entry.expiresAt() != null && entry.expiresAt() <= System.currentTimeMillis()) {
        java.nio.file.Files.deleteIfExists(path);
        return null;
      }
      return entry.value();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Stored state is unreadable", e);
    }
  }

  private void storeFile(String key, String value, Duration ttl) {
    var path = file(key);
    try {
      if (!java.nio.file.Files.exists(path)) {
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of(fileDir))) {
          if (files.count() >= 20000)
            throw new jakarta.ws.rs.ClientErrorException("State capacity reached", 429);
        }
      }
      var temp =
          java.nio.file.Files.createTempFile(java.nio.file.Path.of(fileDir), ".state-", ".tmp");
      mapper.writeValue(
          temp.toFile(),
          new FileEntry(value, ttl == null ? null : System.currentTimeMillis() + ttl.toMillis()));
      java.nio.file.Files.move(
          temp,
          path,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("State cannot be written", e);
    }
  }

  /** {@code redis}, {@code file} or {@code memory}. */
  public String adapter() {
    return persistent ? "redis" : fileDir != null ? "file" : "memory";
  }

  private String key(String tenant, String namespace, String id) {
    if (!tenant.matches("[A-Za-z0-9_-]{1,64}")
        || !namespace.matches("[a-z][a-z-]*")
        || !id.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid state identity");
    return "qwenbridge:platform:" + tenant + ":" + namespace + ":" + id;
  }

  private String raw(String key) {
    if (persistent) return redisSource.get().value(String.class).get(key);
    if (fileDir != null) return loadFile(key);
    Entry e = memory.get(key);
    if (e != null && e.expires() != null && !e.expires().isAfter(Instant.now())) {
      memory.remove(key, e);
      return null;
    }
    return e == null ? null : e.value();
  }

  public <T> T read(String tenant, String namespace, String id, Class<T> type) {
    return decode(raw(key(tenant, namespace, id)), type);
  }

  public <T> T update(
      String tenant,
      String namespace,
      String id,
      Class<T> type,
      Duration ttl,
      UnaryOperator<T> change) {
    String key = key(tenant, namespace, id);
    if (fileDir != null) {
      synchronized (this) {
        T next = change.apply(decode(loadFile(key), type));
        String after = encode(next);
        if (after.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxValueBytes)
          throw new jakarta.ws.rs.ClientErrorException("State value exceeds the size limit", 413);
        storeFile(key, after, ttl);
        return next;
      }
    }
    for (int attempt = 0; attempt < 20; attempt++) {
      String before = raw(key);
      T next = change.apply(decode(before, type));
      String after = encode(next);
      if (after.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxValueBytes)
        throw new jakarta.ws.rs.ClientErrorException("State value exceeds the size limit", 413);
      if (persistent) {
        var result =
            redisSource
                .get()
                .execute(
                    "EVAL",
                    "local old=redis.call('GET',KEYS[1]); if (old or '')~=ARGV[1] then return 0"
                        + " end; if ARGV[3]=='0' then redis.call('SET',KEYS[1],ARGV[2]) else"
                        + " redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]) end; return 1",
                    "1",
                    key,
                    before == null ? "" : before,
                    after,
                    ttl == null ? "0" : Long.toString(Math.max(1, ttl.toSeconds())));
        if ("1".equals(result.toString())) return next;
      } else {
        memory
            .entrySet()
            .removeIf(
                e ->
                    e.getValue().expires() != null
                        && !e.getValue().expires().isAfter(Instant.now()));
        if (memory.size() >= 20000 && !memory.containsKey(key))
          throw new jakarta.ws.rs.ClientErrorException("State capacity reached", 429);
        Entry entry = new Entry(after, ttl == null ? null : Instant.now().plus(ttl));
        if (before == null
            ? memory.putIfAbsent(key, entry) == null
            : memory.computeIfPresent(key, (k, v) -> v.value().equals(before) ? entry : v) == entry)
          return next;
      }
    }
    throw new jakarta.ws.rs.ClientErrorException("Concurrent state update; retry", 409);
  }

  private String encode(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid state", ex);
    }
  }

  private <T> T decode(String raw, Class<T> type) {
    if (raw == null) return null;
    try {
      return mapper.readValue(raw, type);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Stored state is invalid", ex);
    }
  }
}
