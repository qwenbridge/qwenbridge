package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Content-addressed evidence has bounded retention and never crosses a tenant keyspace. */
@ApplicationScoped
public class CommerceEvidenceStore {
  private static final Duration RETENTION = Duration.ofHours(1);
  private static final int FALLBACK_ENTRIES = 100_000;
  private final ValueCommands<String, String> values;

  /** QB-205: in-process storage when {@code qwenbridge.platform.store=memory}; Redis otherwise. */
  private final io.qwenbridge.platform.LocalTtlStore local;

  private final ObjectMapper mapper;

  public CommerceEvidenceStore(RedisDataSource redis, ObjectMapper mapper) {
    this.values = redis.value(String.class);
    this.local = null;
    this.mapper = mapper.copy();
  }

  @jakarta.inject.Inject
  public CommerceEvidenceStore(
      jakarta.enterprise.inject.Instance<RedisDataSource> redis,
      ObjectMapper mapper,
      @org.eclipse.microprofile.config.inject.ConfigProperty(name = "qwenbridge.platform.store")
          java.util.Optional<String> store) {
    // Short-lived data: in-process for the memory and file stores, so neither needs Redis.
    boolean memory = store.filter(s -> s.equals("memory") || s.equals("file")).isPresent();
    this.values = memory ? null : redis.get().value(String.class);
    this.local =
        memory
            ? new io.qwenbridge.platform.LocalTtlStore(100_000, java.time.Clock.systemUTC())
            : null;
    this.mapper = mapper.copy();
  }

  /**
   * QB-293: Redis is an optional short-lived cache. When a Redis call fails, this instance keeps
   * working with a bounded in-process store (same TTLs) instead of failing the request; the outage
   * is logged at most once a minute. Data written during the outage is local to this instance.
   */
  private final io.qwenbridge.platform.LocalTtlStore fallback =
      new io.qwenbridge.platform.LocalTtlStore(FALLBACK_ENTRIES, java.time.Clock.systemUTC());

  private final java.util.concurrent.atomic.AtomicLong lastOutageLog =
      new java.util.concurrent.atomic.AtomicLong();

  private void redisUnavailable(RuntimeException e) {
    long now = System.currentTimeMillis();
    long last = lastOutageLog.get();
    if (now - last > 60_000 && lastOutageLog.compareAndSet(last, now))
      org.jboss.logging.Logger.getLogger(getClass())
          .warnf(
              "Redis unavailable (%s); using the in-process fallback",
              e.getClass().getSimpleName());
  }

  private String read(String key) {
    if (local != null) return local.get(key);
    try {
      String value = values.get(key);
      return value != null ? value : fallback.get(key);
    } catch (RuntimeException e) {
      redisUnavailable(e);
      return fallback.get(key);
    }
  }

  private void write(String key, String value, boolean onlyIfAbsent) {
    if (local != null) {
      if (onlyIfAbsent) local.setIfAbsent(key, value, RETENTION);
      else local.set(key, value, RETENTION);
      return;
    }
    try {
      if (onlyIfAbsent) values.set(key, value, new SetArgs().nx().ex(RETENTION));
      else values.set(key, value, new SetArgs().ex(RETENTION));
    } catch (RuntimeException e) {
      redisUnavailable(e);
      if (onlyIfAbsent) fallback.setIfAbsent(key, value, RETENTION);
      else fallback.set(key, value, RETENTION);
    }
  }

  public Attribution record(
      String tenant, String source, String claim, List<Evidence> spans, String parent) {
    String id =
        ApiIdentity.digest(tenant + "\n" + source + "\n" + claim + "\n" + spans + "\n" + parent);
    String key = "qwenbridge:evidence:" + tenant + ":" + id;
    Attribution existing = get(tenant, id);
    if (existing != null) return existing;
    var now = Instant.now();
    var attribution = new Attribution(id, tenant, "", source, now, now, claim, spans, parent);
    try {
      write(key, mapper.writeValueAsString(attribution), true);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Cannot encode evidence", ex);
    }
    return attribution;
  }

  public Attribution get(String tenant, String id) {
    if (id == null || !id.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Invalid evidence identifier");
    String raw = read("qwenbridge:evidence:" + tenant + ":" + id);
    try {
      return raw == null ? null : mapper.readValue(raw, Attribution.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Stored evidence is invalid", ex);
    }
  }

  public void bindOwner(String tenant, String id, String subject) {
    if (get(tenant, id) == null) throw new IllegalArgumentException("Evidence is unavailable");
    write("qwenbridge:evidence-owner:" + tenant + ":" + id, subject, false);
  }

  public String owner(String tenant, String id) {
    return read("qwenbridge:evidence-owner:" + tenant + ":" + id);
  }

  public Attribution recordSource(
      String tenant,
      String source,
      String text,
      String claim,
      Instant occurredAt,
      List<Evidence> spans,
      String parent) {
    return recordSource(tenant, source, text, claim, occurredAt, spans, parent, "", true);
  }

  public Attribution recordQuery(
      String tenant, String requestId, String text, List<Evidence> spans, String parent) {
    return recordSource(
        tenant,
        "urn:qwenbridge:query:" + requestId,
        text,
        "Translation and explicit search constraints",
        Instant.now(),
        spans,
        parent,
        requestId,
        false);
  }

  private Attribution recordSource(
      String tenant,
      String source,
      String text,
      String claim,
      Instant occurredAt,
      List<Evidence> spans,
      String parent,
      String requestId,
      boolean external) {
    if (source == null
        || text == null
        || text.length() > 32768
        || claim == null
        || claim.isBlank()
        || claim.length() > 2048
        || occurredAt == null) throw new IllegalArgumentException("Invalid source evidence");
    var uri = java.net.URI.create(source);
    if (uri.getScheme() == null || !java.util.Set.of("urn", "https").contains(uri.getScheme()))
      throw new IllegalArgumentException("Evidence source must be a URN or HTTPS reference");
    for (var span : spans) span.verify(text);
    if (parent != null && get(tenant, parent) == null)
      throw new IllegalArgumentException("Parent evidence unavailable in this tenant");
    String id = ApiIdentity.digest(tenant + source + text + claim + occurredAt + spans + parent);
    var item =
        new Attribution(
            id,
            tenant,
            requestId,
            (external ? "external:" : "") + source,
            occurredAt,
            Instant.now(),
            claim,
            spans,
            parent);
    try {
      write("qwenbridge:evidence:" + tenant + ":" + id, mapper.writeValueAsString(item), false);
      write("qwenbridge:evidence-source:" + tenant + ":" + id, text, false);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
    return item;
  }

  public String sourceText(String tenant, String id) {
    if (get(tenant, id) == null) return null;
    return read("qwenbridge:evidence-source:" + tenant + ":" + id);
  }
}
