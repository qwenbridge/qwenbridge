package io.qwenbridge.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CatalogNormalizerTest {
  private final CatalogNormalizer normalizer = new CatalogNormalizer();

  @Test
  void normalizesVariantsMoneyUnitsAttributesAndSourceWithoutInventingStock() {
    var product =
        normalizer.normalize(
            "feed",
            "parent",
            Map.of(
                "title",
                " Running shoes ",
                "vendor",
                "Nike",
                "productType",
                "Footwear",
                "currency",
                "eur",
                "variants",
                List.of(
                    Map.of(
                        "id",
                        "v1",
                        "price",
                        "99.90",
                        "color",
                        "Noir",
                        "size",
                        "４２",
                        "quantity",
                        2,
                        "weight",
                        Map.of("value", 500, "unit", "g")),
                    Map.of("id", "v2", "price", "109.00", "color", "Rouge", "size", "41"))));
    assertThat(product.category()).isEqualTo("shoes");
    assertThat(product.variants()).hasSize(2);
    var first = product.variants().getFirst();
    assertThat(first.price().currency()).isEqualTo("EUR");
    assertThat(first.weightKg()).isEqualByComparingTo("0.5");
    assertThat(first.attributes()).containsEntry("color", "black").containsEntry("size", "42");
    assertThat(product.variants().get(1).stock().availability())
        .isEqualTo(CatalogProduct.Availability.UNKNOWN);
    assertThat(normalizer.document(product, product.variants().get(1), "tenant"))
        .doesNotContainKey("inStock");
    assertThat(product.source().recordId()).isEqualTo("parent");
  }

  @Test
  void refusesAmbiguousOrContradictoryDataAndDoesNotLetAttributesOverrideIdentity() {
    assertThatThrownBy(() -> new CatalogProduct.Money(new BigDecimal("1.5"), "JPY"))
        .hasMessageContaining("amount");
    assertThatThrownBy(() -> new CatalogProduct.Stock(CatalogProduct.Availability.OUT_OF_STOCK, 2))
        .hasMessageContaining("Contradictory");
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "feed", "p", Map.of("title", "shoe", "price", "1,500", "currency", "EUR")))
        .hasMessageContaining("number");
    var product =
        normalizer.normalize(
            "feed",
            "p",
            Map.of(
                "title",
                "shoe",
                "price",
                20,
                "currency",
                "EUR",
                "attributes",
                Map.of("tenantId", "forged", "title", "forged", "color", "black")));
    assertThat(normalizer.document(product, product.variants().getFirst(), "real"))
        .containsEntry("tenantId", "real")
        .containsEntry("title", "shoe");
  }
}
