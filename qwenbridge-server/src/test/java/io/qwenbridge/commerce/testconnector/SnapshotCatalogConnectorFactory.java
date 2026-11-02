package io.qwenbridge.commerce.testconnector;

import io.qwenbridge.catalog.connector.CatalogConnector;
import io.qwenbridge.catalog.connector.CatalogConnectorFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only catalog connector kind {@code test-snapshot} (QB-287). It replaces the removed Shopify
 * connector in the catalog-sync tests: a complete-snapshot source that serves one product whose
 * variant count and price the test controls, so removal of missing variants and price refresh stay
 * covered without any provider-specific code.
 */
public final class SnapshotCatalogConnectorFactory implements CatalogConnectorFactory {

  public static final String KIND = "test-snapshot";

  /** Number of variants the next snapshot contains; 2 gives price 99, otherwise price 88. */
  public static final AtomicInteger VARIANTS = new AtomicInteger(2);

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public CatalogConnector open(URI endpoint, String token) {
    return new CatalogConnector() {
      @Override
      public Page fetch(String cursor) {
        int count = VARIANTS.get();
        var variants = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++)
          variants.add(Map.of("id", "variant-" + i, "price", count == 2 ? "99" : "88"));
        Map<String, Object> product =
            Map.of(
                "title", "Shoes",
                "brand", "Nike",
                "category", "shoes",
                "currency", "USD",
                "variants", variants);
        return new Page(List.of(new Change("product", false, product)), "end", false);
      }

      @Override
      public boolean completeSnapshot() {
        return true;
      }
    };
  }
}
