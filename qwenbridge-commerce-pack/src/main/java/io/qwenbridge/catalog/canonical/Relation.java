package io.qwenbridge.catalog.canonical;

import io.qwenbridge.catalog.CatalogProduct.RelationType;

/** A typed product relationship with its provenance. */
public record Relation(RelationType type, String targetProductId, Provenance provenance) {
  public Relation {
    if (type == null || targetProductId == null || targetProductId.isBlank() || provenance == null)
      throw new IllegalArgumentException("Invalid catalog relation");
  }
}
