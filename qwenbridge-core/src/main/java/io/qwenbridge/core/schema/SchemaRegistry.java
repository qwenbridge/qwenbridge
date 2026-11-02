package io.qwenbridge.core.schema;

import io.qwenbridge.core.schema.SchemaRegistryException.Code;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Thread-safe, in-memory registry of domain schemas (QB-043, QB-046, QB-047, QB-048). It needs no
 * database, cache or network: it is ready as soon as it is constructed.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li><b>Immutable versions.</b> Registering an existing id and version again is idempotent only
 *       when the content is identical; otherwise it fails with {@code IMMUTABLE_VERSION}.
 *   <li><b>Compatible minors.</b> A new minor within an existing major must be backward compatible
 *       with the highest registered version of that major (no field removed, retyped, narrowed or
 *       made stricter; no entity type or capability removed). Otherwise {@code
 *       INCOMPATIBLE_CHANGE}: publish a new major instead.
 *   <li><b>Ownership.</b> The first owner of a schema id owns all its versions. Other owners can
 *       neither add versions nor remove them ({@code OWNERSHIP_VIOLATION}); removing a pack removes
 *       only that pack's schemas.
 * </ul>
 *
 * Reads are lock-free over an immutable snapshot; writes are serialized.
 */
public final class SchemaRegistry {

  private static final Pattern OWNER =
      Pattern.compile("^[a-z][a-z0-9_.-]{0,63}(:[a-z0-9][a-z0-9_.-]{0,63})?$");

  private record Entry(DomainSchema schema, String owner) {}

  private final Object writeLock = new Object();
  private volatile Map<String, TreeMap<SchemaVersion, Entry>> snapshot = Map.of();

  /**
   * Registers a schema version on behalf of {@code owner}, e.g. {@code core} or {@code
   * pack:commerce}.
   */
  public DomainSchema register(String owner, DomainSchema schema) {
    requireOwner(owner);
    Objects.requireNonNull(schema, "schema must not be null");
    synchronized (writeLock) {
      TreeMap<SchemaVersion, Entry> versions = snapshot.get(schema.id());
      if (versions != null) {
        String currentOwner = versions.firstEntry().getValue().owner();
        if (!currentOwner.equals(owner)) {
          throw new SchemaRegistryException(
              Code.OWNERSHIP_VIOLATION, "schema " + schema.id() + " is owned by another owner");
        }
        Entry existing = versions.get(schema.version());
        if (existing != null) {
          if (existing.schema().equals(schema)) {
            return existing.schema();
          }
          throw new SchemaRegistryException(
              Code.IMMUTABLE_VERSION, schema.key() + " already exists with different content");
        }
        boolean newerMinorExists =
            versions.tailMap(schema.version(), false).keySet().stream()
                .anyMatch(v -> v.major() == schema.version().major());
        if (newerMinorExists) {
          throw new SchemaRegistryException(
              Code.INCOMPATIBLE_CHANGE,
              "versions within a major must be registered in ascending order");
        }
        versions.headMap(schema.version(), false).descendingMap().values().stream()
            .map(Entry::schema)
            .filter(previous -> previous.version().major() == schema.version().major())
            .findFirst()
            .ifPresent(previous -> requireCompatible(previous, schema));
      }
      Map<String, TreeMap<SchemaVersion, Entry>> next = copy(snapshot);
      next.computeIfAbsent(schema.id(), id -> new TreeMap<>())
          .put(schema.version(), new Entry(schema, owner));
      snapshot = freeze(next);
      return schema;
    }
  }

  /** Removes one version; only its owner may do so. */
  public void unregister(String owner, SchemaKey key) {
    requireOwner(owner);
    synchronized (writeLock) {
      TreeMap<SchemaVersion, Entry> versions = snapshot.get(key.id());
      Entry entry = versions == null ? null : versions.get(key.version());
      if (entry == null) {
        throw new SchemaRegistryException(Code.NOT_FOUND, key + " is not registered");
      }
      if (!entry.owner().equals(owner)) {
        throw new SchemaRegistryException(
            Code.OWNERSHIP_VIOLATION, key + " is owned by another owner");
      }
      Map<String, TreeMap<SchemaVersion, Entry>> next = copy(snapshot);
      next.get(key.id()).remove(key.version());
      if (next.get(key.id()).isEmpty()) {
        next.remove(key.id());
      }
      snapshot = freeze(next);
    }
  }

  /** Removes every schema owned by {@code owner} (pack deactivation); returns what was removed. */
  public List<SchemaKey> unregisterAll(String owner) {
    requireOwner(owner);
    synchronized (writeLock) {
      List<SchemaKey> removed = new ArrayList<>();
      Map<String, TreeMap<SchemaVersion, Entry>> next = copy(snapshot);
      next.values()
          .forEach(
              versions ->
                  versions
                      .values()
                      .removeIf(
                          entry -> {
                            if (entry.owner().equals(owner)) {
                              removed.add(entry.schema().key());
                              return true;
                            }
                            return false;
                          }));
      next.values().removeIf(TreeMap::isEmpty);
      snapshot = freeze(next);
      return List.copyOf(removed);
    }
  }

  public Optional<DomainSchema> get(SchemaKey key) {
    TreeMap<SchemaVersion, Entry> versions = snapshot.get(key.id());
    Entry entry = versions == null ? null : versions.get(key.version());
    return Optional.ofNullable(entry).map(Entry::schema);
  }

  public Optional<DomainSchema> latest(String id) {
    TreeMap<SchemaVersion, Entry> versions = snapshot.get(id);
    return versions == null
        ? Optional.empty()
        : Optional.of(versions.lastEntry().getValue().schema());
  }

  public List<SchemaVersion> versions(String id) {
    TreeMap<SchemaVersion, Entry> versions = snapshot.get(id);
    return versions == null ? List.of() : List.copyOf(versions.keySet());
  }

  public Optional<String> owner(String id) {
    TreeMap<SchemaVersion, Entry> versions = snapshot.get(id);
    return versions == null
        ? Optional.empty()
        : Optional.of(versions.firstEntry().getValue().owner());
  }

  /** Every registered key, sorted by id then version. */
  public List<SchemaKey> keys() {
    List<SchemaKey> keys = new ArrayList<>();
    new TreeMap<>(snapshot)
        .forEach((id, versions) -> versions.keySet().forEach(v -> keys.add(new SchemaKey(id, v))));
    return List.copyOf(keys);
  }

  /**
   * Throws {@code INCOMPATIBLE_CHANGE} if {@code next} is not backward compatible with {@code
   * previous}.
   */
  static void requireCompatible(DomainSchema previous, DomainSchema next) {
    for (Map.Entry<String, FieldDefinition> e : previous.fields().entrySet()) {
      FieldDefinition before = e.getValue();
      FieldDefinition after = next.fields().get(e.getKey());
      String problem = null;
      if (after == null) {
        problem = "field " + e.getKey() + " was removed";
      } else if (after.type() != before.type()) {
        problem = "field " + e.getKey() + " changed type";
      } else if (!after.operators().containsAll(before.operators())) {
        problem = "field " + e.getKey() + " lost operators";
      } else if (!after.unit().equals(before.unit())) {
        problem = "field " + e.getKey() + " changed unit";
      } else if (before.nullable() && !after.nullable()) {
        problem = "field " + e.getKey() + " is no longer nullable";
      } else if (narrower(before, after)) {
        problem = "field " + e.getKey() + " narrowed its allowed values or bounds";
      }
      if (problem != null) {
        throw new SchemaRegistryException(
            Code.INCOMPATIBLE_CHANGE, problem + "; publish a new major version of " + next.id());
      }
    }
    if (!next.entityTypes().containsAll(previous.entityTypes())) {
      throw new SchemaRegistryException(
          Code.INCOMPATIBLE_CHANGE, "entity types were removed; publish a new major version");
    }
    if (!next.capabilities().containsAll(previous.capabilities())) {
      throw new SchemaRegistryException(
          Code.INCOMPATIBLE_CHANGE, "capabilities were removed; publish a new major version");
    }
  }

  private static boolean narrower(FieldDefinition before, FieldDefinition after) {
    boolean values =
        before.allowedValues().isEmpty()
            ? !after.allowedValues().isEmpty()
            : !after.allowedValues().isEmpty()
                && !after.allowedValues().containsAll(before.allowedValues());
    boolean min =
        after.min().isPresent()
            && (before.min().isEmpty() || after.min().get().compareTo(before.min().get()) > 0);
    boolean max =
        after.max().isPresent()
            && (before.max().isEmpty() || after.max().get().compareTo(before.max().get()) < 0);
    return values || min || max;
  }

  private static void requireOwner(String owner) {
    if (owner == null || !OWNER.matcher(owner).matches()) {
      throw new SchemaRegistryException(
          Code.INVALID_OWNER, "owner must look like 'core' or 'pack:<id>'");
    }
  }

  private static Map<String, TreeMap<SchemaVersion, Entry>> copy(
      Map<String, TreeMap<SchemaVersion, Entry>> source) {
    Map<String, TreeMap<SchemaVersion, Entry>> copy = new HashMap<>();
    source.forEach((id, versions) -> copy.put(id, new TreeMap<>(versions)));
    return copy;
  }

  private static Map<String, TreeMap<SchemaVersion, Entry>> freeze(
      Map<String, TreeMap<SchemaVersion, Entry>> next) {
    // Inner TreeMaps are never mutated after publication: every write copies them first.
    return Collections.unmodifiableMap(next);
  }
}
