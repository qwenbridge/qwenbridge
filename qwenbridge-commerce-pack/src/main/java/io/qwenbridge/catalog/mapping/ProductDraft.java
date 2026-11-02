package io.qwenbridge.catalog.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The output of a {@link CatalogMapping}: source values placed into canonical slots but not yet
 * normalized. Every slot keeps its source path. Optional slots are {@code null}.
 */
public record ProductDraft(
    RawValue id,
    RawValue title,
    RawValue description,
    RawValue brand,
    RawValue category,
    Map<String, RawValue> attributes,
    List<VariantDraft> variants,
    List<RelationDraft> relations) {
  public ProductDraft {
    attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    variants = List.copyOf(variants);
    relations = List.copyOf(relations);
  }

  /** A purchasable variant. Weight is given as a value and a unit, both with their paths. */
  public record VariantDraft(
      RawValue id,
      RawValue sku,
      RawValue price,
      RawValue compareAtPrice,
      RawValue currency,
      RawValue quantity,
      RawValue inStock,
      RawValue availability,
      RawValue weightValue,
      RawValue weightUnit,
      RawValue availableAt,
      Map<String, RawValue> options) {
    public VariantDraft {
      options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }
  }

  public record RelationDraft(RawValue type, RawValue target) {}
}
