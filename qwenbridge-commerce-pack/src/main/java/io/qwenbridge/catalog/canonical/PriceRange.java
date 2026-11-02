package io.qwenbridge.catalog.canonical;

/** The lowest and highest variant price in one currency. */
public record PriceRange(String currency, Price min, Price max) {
  public PriceRange {
    if (min == null || max == null) throw new IllegalArgumentException("Range bounds required");
    min.requireSameCurrency(max);
    if (!min.currency().equals(currency) || min.compareAmount(max) > 0)
      throw new IllegalArgumentException("Invalid price range");
  }
}
