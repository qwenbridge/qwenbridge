package io.qwenbridge.catalog;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.catalog.canonical.*;
import io.qwenbridge.catalog.canonical.AttributeValue.Certainty;
import io.qwenbridge.catalog.canonical.Provenance.Origin;
import io.qwenbridge.catalog.mapping.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-092 – QB-096, QB-098: canonical model, mapping SPI, normalization and provenance. */
class CanonicalCatalogTest {
  private static final Instant OBSERVED = Instant.parse("2026-11-01T00:00:00Z");
  private final CatalogNormalizer catalog =
      new CatalogNormalizer(
          CatalogMappings.builtIn(),
          new ProductNormalizer(CommerceVocabulary.builtIn()),
          Clock.fixed(OBSERVED, ZoneOffset.UTC));

  private CanonicalProduct feed(Map<String, Object> raw) {
    return catalog.canonical("http-json", "rec-1", raw);
  }

  private static Map<String, Object> map(Object... kv) {
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
    return out;
  }

  @Test
  void canonicalProductRecordsProvenanceForEveryNormalizedValue() {
    var product =
        feed(
            map(
                "productId", "p-9",
                "title", "  Trail   runner ",
                "vendor", "NIKE",
                "productType", "Footwear",
                "description", "Light shoe",
                "currency", "eur",
                "attributes", map("colour", "Noir", "Sole", "rubber"),
                "variants",
                    List.of(
                        map("id", "v1", "price", "99.90", "size", "４２", "quantity", 3),
                        map("id", "v2", "price", "109", "size", "43"))));

    assertEquals("1.0", product.schemaVersion());
    assertEquals("p-9", product.id());
    assertEquals("Trail runner", product.title().value());
    assertEquals(new SourceRef("http-json", "rec-1", "http-json", OBSERVED), product.source());

    assertEquals("Nike", product.brand().value());
    assertTrue(product.brand().recognized());
    assertEquals(Origin.NORMALIZED, product.brand().provenance().origin());
    assertEquals("vendor", product.brand().provenance().sourceField());
    assertEquals("NIKE", product.brand().provenance().sourceValue());

    assertEquals("shoes", product.category().id());
    assertEquals(List.of("fashion", "shoes"), product.category().path());
    assertEquals("productType", product.category().provenance().sourceField());
    assertEquals("Footwear", product.category().provenance().sourceValue());

    var color = product.attributes().get("color");
    assertEquals("black", color.value());
    assertEquals(Certainty.CERTAIN, color.certainty());
    assertEquals(Origin.NORMALIZED, color.provenance().origin());
    assertEquals("attributes.colour", color.provenance().sourceField());
    assertEquals("Noir", color.provenance().sourceValue());
    // Unknown attribute names and values are kept as the source gave them.
    assertEquals("rubber", product.attributes().get("Sole").value());
    assertEquals(Origin.SOURCE, product.attributes().get("Sole").provenance().origin());

    var first = product.variants().getFirst();
    assertEquals("42", first.options().get("size").value());
    assertEquals("variants[0].size", first.options().get("size").provenance().sourceField());
    assertEquals(new BigDecimal("99.9"), first.price().amount());
    assertEquals("EUR", first.price().currency());
    assertEquals("variants[0].price", first.priceProvenance().sourceField());
    assertEquals(Availability.IN_STOCK, first.inventory().availability());
    assertEquals(Origin.DERIVED, first.inventory().provenance().origin());
    // No stock data: unknown, never assumed.
    assertEquals(Availability.UNKNOWN, product.variants().get(1).inventory().availability());
    assertNull(product.variants().get(1).inventory().quantity());
  }

  @Test
  void canonicalAttributeNameFromTheSourceWinsOverAnAliasInAnyOrder() {
    for (var attributes :
        List.of(map("colour", "red", "color", "blue"), map("color", "blue", "colour", "red"))) {
      var product =
          feed(map("title", "Cap", "price", 5, "currency", "EUR", "attributes", attributes));
      assertEquals("blue", product.attributes().get("color").value());
      assertEquals(
          "attributes.color", product.attributes().get("color").provenance().sourceField());
    }
  }

  @Test
  void pricesAreExactPerCurrencyAndNeverConverted() {
    var product =
        feed(
            map(
                "title",
                "Mug",
                "variants",
                List.of(
                    map("id", "a", "price", "۱۲۰٫۵۰", "currency", "SEK"),
                    map("id", "b", "price", "9.990", "currency", "EUR", "compareAtPrice", "12"),
                    map("id", "c", "price", "100", "currency", "SEK"))));
    assertEquals(new BigDecimal("120.5"), product.variants().get(0).price().amount());
    assertEquals(new BigDecimal("100"), product.variants().get(2).price().amount());
    assertEquals(0, product.variants().get(2).price().amount().scale());
    var ranges = product.priceRanges();
    assertEquals(List.of("EUR", "SEK"), List.copyOf(ranges.keySet()));
    assertEquals(new BigDecimal("100"), ranges.get("SEK").min().amount());
    assertEquals(new BigDecimal("120.5"), ranges.get("SEK").max().amount());
    assertEquals(new BigDecimal("12"), product.variants().get(1).compareAtPrice().amount());

    var sek = product.variants().get(0).price();
    var eur = product.variants().get(1).price();
    assertThrows(Price.CurrencyMismatch.class, () -> sek.compareAmount(eur));
    assertThrows(
        Price.CurrencyMismatch.class,
        () -> new PriceRange("SEK", sek, eur),
        "a range cannot span currencies");
    var invalid =
        assertThrows(IllegalArgumentException.class, () -> new Price(new BigDecimal("1.5"), "JPY"));
    assertTrue(invalid.getMessage().contains("amount"));
    assertThrows(IllegalArgumentException.class, () -> new Price(BigDecimal.ONE, "XYZ"));
    assertThrows(IllegalArgumentException.class, () -> new Price(new BigDecimal("-1"), "EUR"));
    var thousands =
        assertThrows(
            InvalidCatalogRecord.class,
            () -> feed(map("title", "x", "price", "1,500", "currency", "EUR")));
    assertEquals("price", thousands.issues().getFirst().path());
  }

  @Test
  void availabilityIsMappedAndContradictionsAreRejected() {
    var preorder =
        feed(
            map(
                "title",
                "Console",
                "price",
                1,
                "currency",
                "EUR",
                "availability",
                "https://schema.org/PreOrder"));
    var inventory = preorder.variants().getFirst().inventory();
    assertEquals(Availability.PREORDER, inventory.availability());
    assertEquals(Origin.NORMALIZED, inventory.provenance().origin());
    assertEquals("https://schema.org/PreOrder", inventory.provenance().sourceValue());
    assertEquals(
        CatalogProduct.Availability.UNKNOWN,
        LegacyCatalogView.of(preorder).variants().getFirst().stock().availability(),
        "version 1 has no preorder state and must not claim stock");

    assertEquals(
        Availability.OUT_OF_STOCK,
        feed(map("title", "x", "price", 1, "currency", "EUR", "quantity", 0))
            .variants()
            .getFirst()
            .inventory()
            .availability());
    assertEquals(
        Availability.DISCONTINUED,
        feed(map("title", "x", "price", 1, "currency", "EUR", "availability", "discontinued"))
            .variants()
            .getFirst()
            .inventory()
            .availability());

    for (var contradictory :
        List.of(
            map("availability", "in stock", "inStock", false),
            map("availability", "backorder", "quantity", 3),
            map("inStock", "false", "quantity", 2),
            map("availability", "in_stock", "quantity", 0))) {
      var raw = map("title", "x", "price", 1, "currency", "EUR");
      raw.putAll(contradictory);
      var error = assertThrows(IllegalArgumentException.class, () -> feed(raw), raw.toString());
      assertTrue(error.getMessage().contains("Contradictory"), raw.toString());
    }
    assertThrows(
        InvalidCatalogRecord.class,
        () -> feed(map("title", "x", "price", 1, "currency", "EUR", "availability", "maybe")));
    assertThrows(
        InvalidCatalogRecord.class,
        () -> feed(map("title", "x", "price", 1, "currency", "EUR", "quantity", "1.5")));
  }

  @Test
  void weightConversionsAreDerivedAndKeepTheSourceValue() {
    var pounds =
        feed(
            map(
                "title",
                "Bag",
                "price",
                1,
                "currency",
                "EUR",
                "weight",
                map("value", 2, "unit", "lb")));
    var weight = pounds.variants().getFirst().weight();
    assertEquals(0, new BigDecimal("0.90718474").compareTo(weight.kilograms()));
    assertEquals(Origin.DERIVED, weight.provenance().origin());
    assertEquals("2 lb", weight.provenance().sourceValue());
    assertEquals("lb->kg:0.45359237", weight.provenance().rule());
    var grams = feed(map("title", "Bag", "price", 1, "currency", "EUR", "weightGrams", 500));
    assertEquals(
        0, new BigDecimal("0.5").compareTo(grams.variants().getFirst().weight().kilograms()));
    assertThrows(
        InvalidCatalogRecord.class,
        () ->
            feed(
                map(
                    "title",
                    "Bag",
                    "price",
                    1,
                    "currency",
                    "EUR",
                    "weight",
                    map("value", 1, "unit", "stone"))));
    var shape =
        assertThrows(
            InvalidCatalogRecord.class,
            () -> feed(map("title", "Bag", "price", 1, "currency", "EUR", "weight", 3)));
    assertEquals("weight", shape.issues().getFirst().path());
  }

  @Test
  void attributesMissingFromTheSourceAreInferredFromTheTitleAsUncertainOrAmbiguous() {
    var inferred = feed(map("title", "Svart läder sneaker", "price", 1, "currency", "SEK"));
    var color = inferred.attributes().get("color");
    assertEquals("black", color.value());
    assertEquals(Certainty.UNCERTAIN, color.certainty());
    assertEquals(Origin.INFERRED, color.provenance().origin());
    assertEquals("title", color.provenance().sourceField());
    assertFalse(color.asserted());
    assertFalse(
        LegacyCatalogView.of(inferred).attributes().containsKey("color"),
        "inferred values are not part of the asserted v1 contract");
    assertFalse(ProductTextTemplate.render(inferred).text().contains("color="));

    var ambiguous = feed(map("title", "Black and white sneaker", "price", 1, "currency", "EUR"));
    var both = ambiguous.attributes().get("color");
    assertEquals(Certainty.AMBIGUOUS, both.certainty());
    assertNull(both.value());
    assertEquals(List.of("black", "white"), both.alternatives());

    var stated = feed(map("title", "Black sneaker", "price", 1, "currency", "EUR", "color", "Red"));
    assertEquals("red", stated.attributes().get("color").value());
    assertEquals(Certainty.CERTAIN, stated.attributes().get("color").certainty());
    var option =
        feed(
            map(
                "title",
                "Black sneaker",
                "currency",
                "EUR",
                "variants",
                List.of(map("id", "v", "price", 1, "color", "blue"))));
    assertFalse(option.attributes().containsKey("color"), "a stated option blocks inference");
    // Whole words only: "redundant" does not mention red.
    assertFalse(
        feed(map("title", "Redundant cable", "price", 1, "currency", "EUR"))
            .attributes()
            .containsKey("color"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttributeValue(
                "color",
                AttributeValue.Kind.TEXT,
                "black",
                Certainty.CERTAIN,
                List.of(),
                new Provenance(Origin.INFERRED, "title", "black", "rule")),
        "an inferred value cannot claim certainty");
  }

  @Test
  void mappingRejectionsAreTypedWithTheSourcePath() {
    var mapping = new JsonFeedMapping();
    var result =
        mapping.map(new SourceRecord("feed", "r", OBSERVED, map("title", "x", "variants", "no")));
    var rejected = assertInstanceOf(MappingResult.Rejected.class, result);
    assertEquals("variants", rejected.issues().getFirst().path());
    var notAMap =
        assertInstanceOf(
            MappingResult.Rejected.class,
            mapping.map(
                new SourceRecord(
                    "feed", "r", OBSERVED, map("title", "x", "variants", List.of(1)))));
    assertEquals("variants[0]", notAMap.issues().getFirst().path());
    var title =
        assertThrows(InvalidCatalogRecord.class, () -> feed(map("price", 1, "currency", "EUR")));
    assertEquals("Catalog requires title", title.getMessage());
    assertThrows(IllegalArgumentException.class, () -> catalog.canonical("feed", "r", null));
    var big = new LinkedHashMap<String, Object>();
    for (int i = 0; i < 129; i++) big.put("k" + i, i);
    assertThrows(IllegalArgumentException.class, () -> feed(big));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            feed(
                map(
                    "title",
                    "x",
                    "currency",
                    "EUR",
                    "variants",
                    List.of(map("id", "a", "price", 1), map("id", "a", "price", 2)))),
        "duplicate variant ids");
  }

  @Test
  void mappingRegistryIsPackOwnedAndRejectsDuplicates() {
    var registry = CatalogMappings.builtIn();
    assertEquals("http-json", registry.forConnector("feed").id());
    // QB-293, QB-287: QwenBridge has no Shopify mapping; an unknown connector kind uses the
    // generic JSON feed mapping.
    assertTrue(registry.find("shopify").isEmpty());
    assertEquals("http-json", registry.forConnector("shopify").id());
    assertThrows(IllegalArgumentException.class, () -> registry.with(new JsonFeedMapping()));
    CatalogMapping custom =
        new CatalogMapping() {
          public String id() {
            return "csv";
          }

          public MappingResult map(SourceRecord record) {
            return MappingResult.reject("row", "not supported");
          }
        };
    var extended = registry.with(custom);
    assertEquals("csv", extended.forConnector("csv").id());
    assertTrue(registry.find("csv").isEmpty(), "registries are immutable");
  }

  @Test
  void vocabularyRejectsAmbiguousAliasesUnknownParentsAndCycles() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CatalogVocabulary.builder("v")
                .attributeValue("color", "black", "dark")
                .attributeValue("color", "navy", "dark")
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () -> CatalogVocabulary.builder("v").category("shoes", "fashion").build());
    assertThrows(
        IllegalArgumentException.class,
        () -> CatalogVocabulary.builder("v").category("a", "b").category("b", "a").build());
    assertThrows(
        IllegalArgumentException.class,
        () -> CatalogVocabulary.builder("v").inferable("x").build());
    var vocabulary = CommerceVocabulary.builtIn();
    assertEquals("black", vocabulary.attributeValue("color", " SCHWARZ ").orElseThrow());
    assertEquals("shoes", vocabulary.category("کفش").orElseThrow());
    assertEquals(List.of("electronics", "laptop"), vocabulary.categoryPath("laptop"));
    assertTrue(vocabulary.brand("Unknown Co").isEmpty());
  }
}
