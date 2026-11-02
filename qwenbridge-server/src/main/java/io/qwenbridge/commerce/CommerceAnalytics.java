package io.qwenbridge.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.list.ListCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class CommerceAnalytics {
  public record Event(
      String requestId, String kind, String queryDigest, List<String> productIds, Instant at) {
    public Event {
      productIds = List.copyOf(productIds);
    }
  }

  public record StoredSearch(String subject, CommerceContracts.SearchResponse response) {}

  private static final int FALLBACK_ENTRIES = 20_000;

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

  private final ListCommands<String, String> lists;
  private final ValueCommands<String, String> values;
  private final ObjectMapper mapper;
  private final RedisDataSource redis;

  /** QB-205: in-process storage when {@code qwenbridge.platform.store=memory}; Redis otherwise. */
  private final io.qwenbridge.platform.LocalTtlStore local;

  public CommerceAnalytics(RedisDataSource redis, ObjectMapper mapper) {
    this.redis = redis;
    lists = redis.list(String.class);
    values = redis.value(String.class);
    local = null;
    this.mapper = mapper.copy();
  }

  @jakarta.inject.Inject
  public CommerceAnalytics(
      jakarta.enterprise.inject.Instance<RedisDataSource> redis,
      ObjectMapper mapper,
      @org.eclipse.microprofile.config.inject.ConfigProperty(name = "qwenbridge.platform.store")
          java.util.Optional<String> store) {
    // Short-lived data: in-process for the memory and file stores, so neither needs Redis.
    boolean memory = store.filter(s -> s.equals("memory") || s.equals("file")).isPresent();
    this.redis = memory ? null : redis.get();
    this.lists = memory ? null : this.redis.list(String.class);
    this.values = memory ? null : this.redis.value(String.class);
    this.local =
        memory
            ? new io.qwenbridge.platform.LocalTtlStore(20_000, java.time.Clock.systemUTC())
            : null;
    this.mapper = mapper.copy();
  }

  public void search(ApiIdentity.Principal caller, CommerceContracts.SearchResponse response) {
    String requestId = response.requestId();
    if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid request identifier");
    write(
        "qwenbridge:search:" + caller.tenant() + ":" + requestId,
        new StoredSearch(caller.subject(), response));
    append(
        caller.tenant(),
        new Event(
            requestId,
            "search",
            ApiIdentity.digest(response.translation().original()),
            response.results().hits().stream()
                .map(io.qwenbridge.execution.provider.model.SearchHit::id)
                .toList(),
            Instant.now()));
  }

  public CommerceContracts.SearchResponse previous(ApiIdentity.Principal caller, String id) {
    if (id == null || !id.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid request identifier");
    String key = "qwenbridge:search:" + caller.tenant() + ":" + id;
    String raw;
    if (local != null) raw = local.get(key);
    else
      try {
        raw = values.get(key);
        if (raw == null) raw = fallback.get(key);
      } catch (RuntimeException e) {
        redisUnavailable(e);
        raw = fallback.get(key);
      }
    try {
      if (raw == null) throw new jakarta.ws.rs.NotFoundException("Search expired or unavailable");
      StoredSearch stored = mapper.readValue(raw, StoredSearch.class);
      if (!stored.subject().equals(caller.subject())) throw new jakarta.ws.rs.NotFoundException();
      return stored.response();
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public void click(ApiIdentity.Principal caller, String requestId, String productId) {
    var previous = previous(caller, requestId);
    if (previous.results().hits().stream().noneMatch(hit -> hit.id().equals(productId)))
      throw new IllegalArgumentException("Product was not in the search results");
    append(caller.tenant(), new Event(requestId, "click", "", List.of(productId), Instant.now()));
  }

  public List<Event> recent(String tenant) {
    String key = "qwenbridge:analytics:" + tenant;
    List<String> rows;
    if (local != null) rows = local.range(key, 1000);
    else
      try {
        rows = new java.util.ArrayList<>(lists.lrange(key, 0, 999));
        rows.addAll(fallback.range(key, 1000));
      } catch (RuntimeException e) {
        redisUnavailable(e);
        rows = fallback.range(key, 1000);
      }
    return rows.stream()
        .map(
            raw -> {
              try {
                return mapper.readValue(raw, Event.class);
              } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException(ex);
              }
            })
        .toList();
  }

  private void append(String tenant, Event event) {
    try {
      String key = "qwenbridge:analytics:" + tenant;
      if (local != null) {
        local.push(key, mapper.writeValueAsString(event), 10_000);
        return;
      }
      String row = mapper.writeValueAsString(event);
      try {
        lists.lpush(key, row);
        lists.ltrim(key, 0, 9999);
      } catch (RuntimeException e) {
        redisUnavailable(e);
        fallback.push(key, row, 10_000);
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void write(String key, Object value) {
    if (local != null) {
      try {
        if (!local.setIfAbsent(
            key, mapper.writeValueAsString(value), java.time.Duration.ofMinutes(10)))
          throw new jakarta.ws.rs.ClientErrorException("Request identifier already exists", 409);
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        throw new IllegalStateException(ex);
      }
      return;
    }
    String json;
    try {
      json = mapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
    Object result;
    try {
      String index = key.substring(0, key.lastIndexOf(':')) + ":recent";
      result =
          redis.execute(
              "EVAL",
              "if redis.call('EXISTS',KEYS[1])==1 then return 0 end;"
                  + " redis.call('SET',KEYS[1],ARGV[1],'EX',600);"
                  + " redis.call('LPUSH',KEYS[2],KEYS[1]); while redis.call('LLEN',KEYS[2])>2048 do"
                  + " local old=redis.call('RPOP',KEYS[2]); redis.call('DEL',old); end;"
                  + " redis.call('EXPIRE',KEYS[2],600); return 1",
              "2",
              key,
              index,
              json);
    } catch (RuntimeException e) {
      redisUnavailable(e);
      result = fallback.setIfAbsent(key, json, java.time.Duration.ofMinutes(10)) ? "1" : "0";
    }
    if (!"1".equals(String.valueOf(result)))
      throw new jakarta.ws.rs.ClientErrorException("Request identifier already exists", 409);
  }
}
