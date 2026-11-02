package io.qwenbridge.catalog.canonical;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A purchasable variant (QB-095).
 *
 * @param sku {@code null} when the source has none
 * @param options variant-specific attributes such as size or color, by canonical name
 * @param compareAtPrice optional reference price; must be in the same currency as {@code price}
 * @param weight {@code null} when the source has none
 * @param availableAt {@code null} when the source has none
 */
public record CanonicalVariant(
    String id,
    SourcedText sku,
    Map<String, AttributeValue> options,
    Price price,
    Provenance priceProvenance,
    Price compareAtPrice,
    Inventory inventory,
    Weight weight,
    LocalDate availableAt) {
  public CanonicalVariant {
    if (id == null || id.isBlank() || price == null || priceProvenance == null || inventory == null)
      throw new IllegalArgumentException("Invalid catalog variant");
    options = Collections.unmodifiableSortedMap(new TreeMap<>(options));
    options.forEach(
        (name, value) -> {
          if (!name.equals(value.name()))
            throw new IllegalArgumentException("Option key must match its name");
        });
    if (compareAtPrice != null) price.requireSameCurrency(compareAtPrice);
  }
}
