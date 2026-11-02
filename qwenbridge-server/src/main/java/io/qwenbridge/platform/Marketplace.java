package io.qwenbridge.platform;

import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.*;

/**
 * Local marketplace foundation: validated publisher artifacts, immutable versions and explicit
 * installation.
 */
@ApplicationScoped
public final class Marketplace {
  public record Listing(
      String id,
      String version,
      String publisher,
      String ownerTenant,
      String digest,
      PackManifest manifest,
      Instant publishedAt,
      ManagedPacks.PackageMetadata metadata) {}

  public record Publication(Listing listing, ManagedPacks.SignedPack artifact) {}

  public record Catalog(Map<String, Publication> publications) {
    public Catalog {
      publications = Map.copyOf(publications);
    }
  }

  private final PlatformStore store;
  private final ManagedPacks packs;
  private final Entitlements entitlements;

  public Marketplace(PlatformStore store, ManagedPacks packs, Entitlements entitlements) {
    this.store = store;
    this.packs = packs;
    this.entitlements = entitlements;
  }

  public Listing publish(ApiIdentity.Principal caller, ManagedPacks.SignedPack artifact) {
    caller.require("marketplace:publish");
    entitlements.require(caller.tenant(), "marketplace-publish");
    var version = packs.inspectExecutable(artifact);
    var m = version.manifest();
    if (!caller.roles().contains("publisher:" + m.publisher()))
      throw new jakarta.ws.rs.ForbiddenException("Publisher permission required");
    var listing =
        new Listing(
            m.id(),
            m.version(),
            m.publisher(),
            caller.tenant(),
            version.digest(),
            m,
            Instant.now(),
            packs.metadataOf(artifact));
    store.update(
        "system",
        "marketplace",
        "catalog",
        Catalog.class,
        null,
        current -> {
          var rows =
              new HashMap<>(
                  current == null ? Map.<String, Publication>of() : current.publications());
          String key = m.id() + "@" + m.version();
          var old = rows.get(key);
          if (old != null
              && (!old.listing().digest().equals(listing.digest())
                  || !old.listing().manifest().equals(listing.manifest())
                  || !old.listing().ownerTenant().equals(caller.tenant())))
            throw new IllegalArgumentException(
                "Published versions are immutable and publisher-owned");
          if (rows.size() >= 100 && !rows.containsKey(key))
            throw new IllegalArgumentException("Marketplace catalog capacity reached");
          rows.putIfAbsent(key, new Publication(listing, artifact));
          return new Catalog(rows);
        });
    return listing;
  }

  public List<Listing> list() {
    var catalog = store.read("system", "marketplace", "catalog", Catalog.class);
    return catalog == null
        ? List.of()
        : catalog.publications().values().stream()
            .map(Publication::listing)
            .sorted(Comparator.comparing(Listing::id).thenComparing(Listing::version))
            .toList();
  }

  public ManagedPacks.SignedPack download(String id, String version) {
    PackManifest.validId(id);
    PackManifest.versionParts(version);
    var catalog = store.read("system", "marketplace", "catalog", Catalog.class);
    var row = catalog == null ? null : catalog.publications().get(id + "@" + version);
    if (row == null) throw new jakarta.ws.rs.NotFoundException();
    packs.inspect(row.artifact());
    return row.artifact();
  }
}
