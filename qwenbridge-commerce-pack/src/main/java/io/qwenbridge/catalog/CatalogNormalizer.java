package io.qwenbridge.catalog;

import static io.qwenbridge.catalog.CatalogProduct.*;

import io.qwenbridge.catalog.canonical.CanonicalProduct;
import io.qwenbridge.catalog.canonical.CommerceVocabulary;
import io.qwenbridge.catalog.canonical.LegacyCatalogView;
import io.qwenbridge.catalog.canonical.ProductNormalizer;
import io.qwenbridge.catalog.mapping.CatalogMappings;
import io.qwenbridge.catalog.mapping.SourceRecord;
import java.time.Clock;
import java.util.*;

/**
 * Catalog normalization entry point. Every record goes through the pack-owned mapping for its
 * connector kind and the canonical normalizer (QB-092 – QB-096); {@link #normalize} projects the
 * result onto the version 1 {@link CatalogProduct} contract.
 */
public final class CatalogNormalizer {
  private final CatalogMappings mappings;
  private final ProductNormalizer normalizer;
  private final Clock clock;

  public CatalogNormalizer() {
    this(
        CatalogMappings.builtIn(),
        new ProductNormalizer(CommerceVocabulary.builtIn()),
        Clock.systemUTC());
  }

  public CatalogNormalizer(CatalogMappings mappings, ProductNormalizer normalizer, Clock clock) {
    this.mappings = Objects.requireNonNull(mappings, "mappings");
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** The canonical product with provenance, through the mapping registered for the connector. */
  public CanonicalProduct canonical(String connector, String recordId, Map<String, Object> raw) {
    if (raw == null) throw new IllegalArgumentException("Invalid catalog document");
    return normalizer.normalize(
        new SourceRecord(connector, recordId, clock.instant(), raw),
        mappings.forConnector(connector));
  }

  public CatalogProduct normalize(String connector, String recordId, Map<String, Object> raw) {
    return LegacyCatalogView.of(canonical(connector, recordId, raw));
  }

  public Map<String, Object> document(CatalogProduct product, Variant variant, String tenant) {
    Map<String, Object> out = new HashMap<>();
    out.put("catalogVersion", product.version());
    out.put("domain", "commerce");
    out.put("productId", product.id());
    out.put("variantId", variant.id());
    out.put("title", product.title());
    out.put("brand", product.brand());
    out.put("category", product.category());
    out.put("description", product.description());
    out.put("attributes", variant.attributes());
    for (String field : List.of("color", "size", "condition", "material", "location"))
      if (variant.attributes().containsKey(field)) out.put(field, variant.attributes().get(field));
    out.put("price", variant.price().amount());
    out.put("currency", variant.price().currency());
    out.put("sku", variant.sku());
    if (variant.stock().availability() != Availability.UNKNOWN)
      out.put("inStock", variant.stock().availability() == Availability.IN_STOCK);
    if (variant.stock().quantity() != null) out.put("quantity", variant.stock().quantity());
    if (variant.weightKg() != null) out.put("weightKg", variant.weightKg());
    if (variant.availableAt() != null) out.put("availableAt", variant.availableAt());
    out.put("relations", product.relations());
    out.put("source", product.source());
    out.put("tenantId", tenant);
    return out;
  }
}
