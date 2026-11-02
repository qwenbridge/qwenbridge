package io.qwenbridge.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public final class CatalogSynchronization {
  @jakarta.inject.Inject io.qwenbridge.connectors.CommerceBackends backends;

  private final OpenSearchApiClient sink;
  private final OpenSearchProperties properties;
  private final ValueCommands<String, String> values;
  private final RedisDataSource redis;
  private final ObjectMapper mapper;
  private final String endpoint, token, kind;
  private final io.qwenbridge.ai.service.AIService ai;

  public CatalogSynchronization(
      @RestClient OpenSearchApiClient sink,
      OpenSearchProperties properties,
      RedisDataSource redis,
      ObjectMapper mapper,
      io.qwenbridge.ai.service.AIService ai,
      @ConfigProperty(name = "qwenbridge.catalog.endpoint") java.util.Optional<String> endpoint,
      @ConfigProperty(name = "qwenbridge.catalog.token") java.util.Optional<String> token,
      @ConfigProperty(name = "qwenbridge.catalog.kind", defaultValue = "http-json") String kind) {
    this.sink = sink;
    this.ai = ai;
    this.properties = properties;
    this.redis = redis;
    values = redis.value(String.class);
    this.mapper = mapper.copy();
    this.endpoint = endpoint.orElse("");
    this.token = token.orElse("");
    this.kind = kind;
    // QB-204: http-json is built in; every other kind comes from an optional connector module.
    if (!kind.equals("http-json")
        && io.qwenbridge.catalog.connector.CatalogConnectorFactory.find(kind).isEmpty())
      throw new IllegalArgumentException("Unknown catalog connector " + kind);
  }

  private io.qwenbridge.catalog.connector.CatalogConnector open() {
    if (kind.equals("http-json"))
      return new HttpCatalogConnector(URI.create(endpoint), token, mapper);
    return io.qwenbridge.catalog.connector.CatalogConnectorFactory.find(kind)
        .orElseThrow(() -> new IllegalStateException("Catalog connector " + kind + " unavailable"))
        .open(URI.create(endpoint), token);
  }

  public Map<String, Object> synchronize(ApiIdentity.Principal caller) {
    caller.require("catalog:write");
    if (backends != null) backends.backend(caller.tenant(), null);
    if (endpoint.isBlank())
      throw new IllegalArgumentException("Catalog connector is not configured");
    String checkpoint =
        "qwenbridge:catalog:"
            + caller.tenant()
            + ":"
            + ApiIdentity.digest(
                backends == null ? endpoint : endpoint + "|" + backends.selected());
    String lock = checkpoint + ":lock";
    String owner = UUID.randomUUID().toString();
    // Redis SET NX protects a tenant checkpoint across application instances. QB-287: catalog
    // synchronization is the one feature that requires Redis; without it, say so explicitly.
    try {
      values.set(lock, owner, new SetArgs().nx().ex(Duration.ofMinutes(30)));
    } catch (RuntimeException unavailable) {
      throw new jakarta.ws.rs.ServiceUnavailableException(
          "Catalog synchronization needs Redis for its checkpoint and lock; Redis is not"
              + " reachable");
    }
    if (!owner.equals(values.get(lock)))
      throw new jakarta.ws.rs.ClientErrorException("Catalog sync already running", 409);
    try (var connector = open()) {
      // A complete-snapshot source (each run reads everything) removes what a run did not see.
      boolean snapshot = connector.completeSnapshot();
      String cursor = values.get(checkpoint);
      String scanKey = checkpoint + ":scan";
      String scan = values.get(scanKey);
      if (snapshot && scan == null) {
        scan = UUID.randomUUID().toString();
        values.set(scanKey, scan);
        values.set(checkpoint, "");
        cursor = "";
      }
      String seen = checkpoint + ":seen:" + scan;
      String known = checkpoint + ":known";
      var sets = redis.set(String.class);
      int applied = 0;
      for (int pages = 0; pages < 100; pages++) {
        renew(lock, owner);
        io.qwenbridge.catalog.connector.CatalogConnector.Page page = connector.fetch(cursor);
        renew(lock, owner);
        if (page.hasMore() && page.nextCursor().equals(cursor))
          throw new IllegalStateException("Connector cursor did not advance");
        for (var change : page.changes()) {
          renew(lock, owner);
          String id = caller.tenant() + "-" + ApiIdentity.digest(endpoint + "|" + change.id());
          String manifest = checkpoint + ":record:" + ApiIdentity.digest(change.id());
          if (change.deleted()) {
            var previousIds = sets.smembers(manifest);
            if (previousIds.isEmpty()) previousIds = java.util.Set.of(id);
            for (String previousId : previousIds) delete(caller.tenant(), previousId);
            redis.key().del(manifest);
          } else {
            var normalizer = new io.qwenbridge.catalog.CatalogNormalizer();
            // QB-092/093: the pack mapping for this connector kind builds the canonical product.
            var canonical = normalizer.canonical(kind, change.id(), change.document());
            var product = io.qwenbridge.catalog.canonical.LegacyCatalogView.of(canonical);
            // QB-097: one deterministic, versioned text per product, embedded once for all
            // variants. Price and stock are not part of it, so they never force a re-embedding.
            var text = io.qwenbridge.catalog.canonical.ProductTextTemplate.render(canonical);
            var embedding =
                ai.embed(new io.qwenbridge.ai.contract.EmbeddingRequest(text.text())).vector();
            var currentIds = new java.util.HashSet<String>();
            for (var variant : product.variants()) {
              String variantId =
                  change.document().containsKey("variants")
                      ? caller.tenant()
                          + "-"
                          + ApiIdentity.digest(endpoint + "|" + change.id() + "|" + variant.id())
                      : id;
              currentIds.add(variantId);
              var doc =
                  new java.util.HashMap<>(normalizer.document(product, variant, caller.tenant()));
              doc.put("sourceId", ApiIdentity.digest(endpoint));
              doc.put("sourceRecordId", change.id());
              doc.put("embedding", embedding);
              doc.put("productTextTemplate", text.template());
              doc.put("productTextFingerprint", text.fingerprint());
              // Track before writing so partial page failures can clean up stale variants on retry.
              sets.sadd(manifest, variantId);
              if (backends == null) sink.upsert(properties.index(), variantId, doc);
              else backends.upsert(caller.tenant(), variantId, doc);
              if (snapshot) sets.sadd(seen, variantId);
            }
            for (String oldId : sets.smembers(manifest))
              if (!currentIds.contains(oldId)) {
                delete(caller.tenant(), oldId);
                sets.srem(manifest, oldId);
              }
          }
          applied++;
        }
        if (page.nextCursor() != null) values.set(checkpoint, page.nextCursor());
        if (!page.hasMore()) {
          if (snapshot) {
            var current = sets.smembers(seen);
            for (String old : sets.smembers(known))
              if (!current.contains(old)) {
                renew(lock, owner);
                try {
                  delete(caller.tenant(), old);
                } catch (jakarta.ws.rs.NotFoundException ignored) {
                  /* Idempotent reconciliation. */
                }
              }
            redis.execute(
                "EVAL",
                "if redis.call('EXISTS',KEYS[1])==1 then redis.call('RENAME',KEYS[1],KEYS[2]) else"
                    + " redis.call('DEL',KEYS[2]) end; redis.call('DEL',KEYS[3]);"
                    + " redis.call('SET',KEYS[4],''); return 1",
                "4",
                seen,
                known,
                scanKey,
                checkpoint);
          }
          return Map.of(
              "status",
              "COMPLETE",
              "applied",
              applied,
              "checkpoint",
              snapshot ? "" : page.nextCursor() == null ? "" : page.nextCursor());
        }
        cursor = page.nextCursor();
      }
      return Map.of(
          "status", "PARTIAL", "applied", applied, "checkpoint", cursor == null ? "" : cursor);
    } finally {
      redis.execute(
          "EVAL",
          "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) else return"
              + " 0 end",
          "1",
          lock,
          owner);
    }
  }

  private void delete(String tenant, String id) {
    if (backends != null) {
      backends.delete(tenant, id);
      return;
    }
    try {
      sink.delete(properties.index(), id);
    } catch (jakarta.ws.rs.NotFoundException ignored) {
      /* Idempotent deletion. */
    }
  }

  private void renew(String lock, String owner) {
    var result =
        redis.execute(
            "EVAL",
            "if redis.call('GET',KEYS[1])==ARGV[1] then redis.call('EXPIRE',KEYS[1],1800); return 1"
                + " else return 0 end",
            "1",
            lock,
            owner);
    if (!"1".equals(result.toString()))
      throw new jakarta.ws.rs.ClientErrorException(
          "Catalog lease lost; retry from checkpoint", 409);
  }
}
