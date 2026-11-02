package io.qwenbridge.catalog.canonical;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * An exact decimal amount in one ISO 4217 currency (QB-095). Amounts are never converted between
 * currencies: comparing prices in different currencies is an error, not a guess.
 */
public record Price(BigDecimal amount, String currency) {
  public Price {
    if (amount == null || currency == null)
      throw new IllegalArgumentException("Price and currency are required");
    currency = currency.strip().toUpperCase(Locale.ROOT);
    int digits;
    try {
      digits = Currency.getInstance(currency).getDefaultFractionDigits();
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unsupported ISO currency", ex);
    }
    amount = amount.stripTrailingZeros();
    if (amount.scale() < 0) amount = amount.setScale(0);
    if (amount.signum() < 0 || amount.precision() > 20 || digits < 0 || amount.scale() > digits)
      throw new IllegalArgumentException("Invalid amount for currency " + currency);
  }

  /** Thrown when two prices in different currencies are compared or combined. */
  public static final class CurrencyMismatch extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public CurrencyMismatch(String left, String right) {
      super("No implicit currency conversion: " + left + " vs " + right);
    }
  }

  public int compareAmount(Price other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  public void requireSameCurrency(Price other) {
    if (!currency.equals(other.currency)) throw new CurrencyMismatch(currency, other.currency);
  }
}
