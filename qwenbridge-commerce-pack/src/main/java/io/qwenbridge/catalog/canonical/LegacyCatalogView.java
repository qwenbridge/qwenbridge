package io.qwenbridge.catalog.canonical;

import io.qwenbridge.catalog.CatalogProduct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Projects a canonical product onto the version 1 {@link CatalogProduct} contract, which the {@code
 * /api/v2/catalog/normalize} endpoint and the index documents keep using. Only asserted attributes
 * are projected; inferred and ambiguous ones are not part of that contract.
 */
public final class LegacyCatalogView {
  private LegacyCatalogView() {}

  public static CatalogProduct of(CanonicalProduct product) {
    var common = asserted(product.attributes());
    var variants = new ArrayList<CatalogProduct.Variant>();
    for (var variant : product.variants()) {
      var attributes = new HashMap<>(common);
      attributes.putAll(asserted(variant.options()));
      variants.add(
          new CatalogProduct.Variant(
              variant.id(),
              variant.sku() == null ? "" : variant.sku().value(),
              attributes,
              new CatalogProduct.Money(variant.price().amount(), variant.price().currency()),
              stock(variant.inventory()),
              variant.weight() == null ? null : variant.weight().kilograms(),
              variant.availableAt() == null ? null : variant.availableAt().toString()));
    }
    return new CatalogProduct(
        1,
        product.id(),
        product.title().value(),
        product.brand() == null ? "" : product.brand().value(),
        product.category() == null ? "" : product.category().id(),
        product.description() == null ? "" : product.description().value(),
        common,
        variants,
        product.relations().stream()
            .map(r -> new CatalogProduct.Relation(r.type(), r.targetProductId()))
            .toList(),
        new CatalogProduct.Source(
            product.source().connector(),
            product.source().recordId(),
            product.source().observedAt()));
  }

  /**
   * Version 1 knows only in stock, out of stock and unknown. Discontinued is out of stock; preorder
   * and backorder cannot be bought now but are not out of stock either, so they are unknown there.
   */
  static CatalogProduct.Stock stock(Inventory inventory) {
    var availability =
        switch (inventory.availability()) {
          case IN_STOCK -> CatalogProduct.Availability.IN_STOCK;
          case OUT_OF_STOCK, DISCONTINUED -> CatalogProduct.Availability.OUT_OF_STOCK;
          case PREORDER, BACKORDER, UNKNOWN -> CatalogProduct.Availability.UNKNOWN;
        };
    return new CatalogProduct.Stock(availability, inventory.quantity());
  }

  private static Map<String, String> asserted(Map<String, AttributeValue> attributes) {
    var out = new HashMap<String, String>();
    attributes.forEach(
        (name, value) -> {
          if (value.asserted()) out.put(name, value.value());
        });
    return out;
  }
}
