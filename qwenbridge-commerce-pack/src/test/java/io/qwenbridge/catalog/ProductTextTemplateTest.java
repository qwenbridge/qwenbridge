package io.qwenbridge.catalog;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.catalog.canonical.CanonicalProduct;
import io.qwenbridge.catalog.canonical.CommerceVocabulary;
import io.qwenbridge.catalog.canonical.ProductNormalizer;
import io.qwenbridge.catalog.canonical.ProductTextTemplate;
import io.qwenbridge.catalog.mapping.CatalogMappings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-097: deterministic, versioned product-to-text for embeddings. */
class ProductTextTemplateTest {
  private static CanonicalProduct product(Map<String, Object> raw, Instant at) {
    return new CatalogNormalizer(
            CatalogMappings.builtIn(),
            new ProductNormalizer(CommerceVocabulary.builtIn()),
            Clock.fixed(at, ZoneOffset.UTC))
        .canonical("http-json", "r", raw);
  }

  private static Map<String, Object> map(Object... kv) {
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
    return out;
  }

  private static Map<String, Object> shoe(
      Object price, Object quantity, Map<String, Object> attributes) {
    return map(
        "title", "Trail runner",
        "brand", "nike",
        "category", "sneakers",
        "description", "Grippy\nsole",
        "currency", "EUR",
        "attributes", attributes,
        "variants",
            List.of(
                map("id", "b", "price", price, "size", "43", "color", "noir", "quantity", quantity),
                map("id", "a", "price", "10", "size", "42", "color", "Rouge")));
  }

  @Test
  void rendersTheDocumentedVersionOneLayout() {
    var text =
        ProductTextTemplate.render(
            product(shoe("99", 2, map("material", "mesh", "drop", 8)), Instant.EPOCH));
    assertEquals(ProductTextTemplate.V1, text.template());
    assertEquals(
        """
        title: Trail runner
        brand: Nike
        category: fashion > shoes
        attributes: drop=8; material=mesh
        options: color=black|red; size=42|43
        description: Grippy sole\
        """,
        text.text());
    assertEquals(64, text.fingerprint().length());
  }

  @Test
  void sameProductAlwaysRendersTheSameTextAndFingerprint() {
    var a = product(shoe("99", 2, map("material", "mesh", "drop", 8)), Instant.EPOCH);
    var b =
        product(
            shoe("99", 2, map("drop", 8, "material", "mesh")),
            Instant.parse("2030-01-01T00:00:00Z"));
    assertEquals(ProductTextTemplate.render(a), ProductTextTemplate.render(b));
  }

  @Test
  void volatileCommerceDataDoesNotChangeTheEmbeddingText() {
    var base = ProductTextTemplate.render(product(shoe("99", 2, map()), Instant.EPOCH));
    var repriced = ProductTextTemplate.render(product(shoe("79.50", 0, map()), Instant.EPOCH));
    assertEquals(base.fingerprint(), repriced.fingerprint(), "price and stock are not embedded");
    var retitled = new LinkedHashMap<>(shoe("99", 2, map()));
    retitled.put("title", "Road runner");
    assertNotEquals(
        base.fingerprint(),
        ProductTextTemplate.render(product(retitled, Instant.EPOCH)).fingerprint());
    assertFalse(base.text().contains("99"));
    assertFalse(base.text().contains("EUR"));
  }

  @Test
  void descriptionIsCutOnCodePointsNotCharacters() {
    String emoji = "👟";
    var raw =
        map("title", "Shoe", "price", 1, "currency", "EUR", "description", emoji.repeat(1200));
    String text = ProductTextTemplate.render(product(raw, Instant.EPOCH)).text();
    String description = text.substring(text.indexOf("description: ") + 13);
    assertEquals(1000, description.codePointCount(0, description.length()));
    assertFalse(Character.isHighSurrogate(description.charAt(description.length() - 1)));
  }
}
