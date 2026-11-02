package io.qwenbridge.catalog.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of the pack's catalog mappings, keyed by mapping id. */
public final class CatalogMappings {
  private final Map<String, CatalogMapping> mappings;
  private final String fallback;

  private CatalogMappings(Map<String, CatalogMapping> mappings, String fallback) {
    this.mappings = Collections.unmodifiableMap(new LinkedHashMap<>(mappings));
    this.fallback = fallback;
  }

  /**
   * The Commerce pack's mappings: the built-in JSON feed mapping, plus mappings contributed by
   * optional modules on the classpath ({@link java.util.ServiceLoader}, QB-293). Unknown connectors
   * use the JSON feed mapping. Two mappings with one id are a configuration error.
   */
  public static CatalogMappings builtIn() {
    var all = new LinkedHashMap<String, CatalogMapping>();
    all.put(JsonFeedMapping.ID, new JsonFeedMapping());
    var contributed =
        java.util.ServiceLoader.load(CatalogMapping.class, CatalogMappings.class.getClassLoader())
            .stream()
            .map(java.util.ServiceLoader.Provider::get)
            .sorted(java.util.Comparator.comparing(CatalogMapping::id))
            .toList();
    for (CatalogMapping mapping : contributed)
      if (all.putIfAbsent(mapping.id(), mapping) != null)
        throw new IllegalStateException("Duplicate catalog mapping " + mapping.id());
    return new CatalogMappings(all, JsonFeedMapping.ID);
  }

  public CatalogMappings with(CatalogMapping mapping) {
    if (mapping == null || mapping.id() == null || mapping.id().isBlank())
      throw new IllegalArgumentException("Mapping id required");
    if (mappings.containsKey(mapping.id()))
      throw new IllegalArgumentException("Duplicate catalog mapping " + mapping.id());
    var all = new LinkedHashMap<>(mappings);
    all.put(mapping.id(), mapping);
    return new CatalogMappings(all, fallback);
  }

  public Optional<CatalogMapping> find(String id) {
    return Optional.ofNullable(mappings.get(id));
  }

  /** The mapping registered for this connector kind, else the JSON feed mapping. */
  public CatalogMapping forConnector(String connector) {
    return mappings.getOrDefault(connector, mappings.get(fallback));
  }
}
