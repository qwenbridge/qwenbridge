package io.qwenbridge.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/** Source-independent catalog contract; product identity and purchasable variants are distinct. */
public record CatalogProduct(
    int version,
    String id,
    String title,
    String brand,
    String category,
    String description,
    Map<String, String> attributes,
    List<Variant> variants,
    List<Relation> relations,
    Source source) {
  public CatalogProduct {
    if (version != 1
        || id == null
        || id.isBlank()
        || title == null
        || title.isBlank()
        || source == null) throw new IllegalArgumentException("Invalid catalog product");
    attributes = Map.copyOf(attributes);
    variants = List.copyOf(variants);
    relations = List.copyOf(relations);
    if (variants.isEmpty()
        || variants.size() > 10000
        || variants.stream().map(Variant::id).distinct().count() != variants.size())
      throw new IllegalArgumentException("Product requires unique variants");
  }

  public record Money(BigDecimal amount, String currency) {
    public Money {
      if (amount == null || currency == null)
        throw new IllegalArgumentException("Price and currency are required");
      currency = currency.toUpperCase(java.util.Locale.ROOT);
      int digits;
      try {
        digits = Currency.getInstance(currency).getDefaultFractionDigits();
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Unsupported ISO currency", ex);
      }
      amount = amount.stripTrailingZeros();
      if (amount.signum() < 0 || amount.precision() > 20 || digits < 0 || amount.scale() > digits)
        throw new IllegalArgumentException("Invalid amount for currency " + currency);
    }
  }

  public enum Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    UNKNOWN
  }

  public record Stock(Availability availability, Integer quantity) {
    public Stock {
      if (availability == null
          || quantity != null
              && (quantity < 0
                  || availability != Availability.UNKNOWN
                      && (quantity > 0) != (availability == Availability.IN_STOCK)))
        throw new IllegalArgumentException("Contradictory inventory");
    }
  }

  public record Variant(
      String id,
      String sku,
      Map<String, String> attributes,
      Money price,
      Stock stock,
      BigDecimal weightKg,
      String availableAt) {
    public Variant {
      if (id == null || id.isBlank() || price == null || stock == null)
        throw new IllegalArgumentException("Invalid catalog variant");
      attributes = Map.copyOf(attributes);
      if (weightKg != null && weightKg.signum() < 0)
        throw new IllegalArgumentException("Negative weight");
      if (availableAt != null) java.time.LocalDate.parse(availableAt);
    }
  }

  public enum RelationType {
    ACCESSORY_FOR,
    BUNDLE_COMPONENT,
    ALTERNATIVE_TO
  }

  public record Relation(RelationType type, String targetProductId) {
    public Relation {
      if (type == null || targetProductId == null || targetProductId.isBlank())
        throw new IllegalArgumentException("Invalid catalog relation");
    }
  }

  public record Source(String connector, String recordId, Instant observedAt) {
    public Source {
      if (connector == null
          || connector.isBlank()
          || recordId == null
          || recordId.isBlank()
          || observedAt == null) throw new IllegalArgumentException("Invalid catalog provenance");
    }
  }
}
