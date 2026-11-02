package io.qwenbridge.catalog.mapping;

/**
 * Provider-neutral catalog mapping SPI (QB-093). A mapping only relocates source values into
 * canonical slots and records their paths. It does not normalize, convert currencies or invent
 * values. Transport (HTTP, GraphQL, files) stays in the connector; field meaning stays here, owned
 * by the pack.
 */
public interface CatalogMapping {
  /** Stable identifier, recorded in the canonical product's source reference. */
  String id();

  MappingResult map(SourceRecord record);
}
