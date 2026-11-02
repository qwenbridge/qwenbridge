package io.qwenbridge.catalog.canonical;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The canonical Commerce product (QB-092): one identity, at least one purchasable variant, and
 * provenance for every value. Owned by the Commerce pack; Core never sees this type.
 *
 * @param brand {@code null} when the source has none
 * @param category {@code null} when the source has none
 * @param description {@code null} when the source has none
 * @param attributes product-level attributes by canonical name, including inferred and ambiguous
 *     ones, which are marked as such
 */
public record CanonicalProduct(
    String schemaVersion,
    String id,
    SourcedText title,
    SourcedText description,
    Term brand,
    CategoryRef category,
    Map<String, AttributeValue> attributes,
    List<CanonicalVariant> variants,
    List<Relation> relations,
    SourceRef source) {
  public static final String SCHEMA_VERSION = "1.0";

  public CanonicalProduct {
    if (!SCHEMA_VERSION.equals(schemaVersion)
        || id == null
        || id.isBlank()
        || title == null
        || title.value().isBlank()
        || source == null) throw new IllegalArgumentException("Invalid catalog product");
    attributes = Collections.unmodifiableSortedMap(new TreeMap<>(attributes));
    variants = List.copyOf(variants);
    relations = List.copyOf(relations);
    if (variants.isEmpty()
        || variants.size() > 10000
        || variants.stream().map(CanonicalVariant::id).distinct().count() != variants.size())
      throw new IllegalArgumentException("Product requires unique variants");
  }

  /**
   * The price range per currency. Variants priced in different currencies produce separate ranges;
   * they are never converted into one.
   */
  public SortedMap<String, PriceRange> priceRanges() {
    var ranges = new TreeMap<String, PriceRange>();
    for (var variant : variants) {
      Price price = variant.price();
      ranges.merge(
          price.currency(),
          new PriceRange(price.currency(), price, price),
          (a, b) ->
              new PriceRange(
                  a.currency(),
                  a.min().compareAmount(b.min()) <= 0 ? a.min() : b.min(),
                  a.max().compareAmount(b.max()) >= 0 ? a.max() : b.max()));
    }
    return Collections.unmodifiableSortedMap(ranges);
  }
}
