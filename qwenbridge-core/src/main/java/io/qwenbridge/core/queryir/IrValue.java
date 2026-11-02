package io.qwenbridge.core.queryir;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Typed constraint values (QB-030, QB-031, QB-038). Numbers and money use {@link BigDecimal}; money
 * never implies a conversion rate; units and currencies are kept exactly as resolved. Temporal
 * values keep ISO-8601 text plus the explicit reference needed to resolve relative windows.
 */
public sealed interface IrValue {

  /** Discriminator used in JSON ({@code "type"}). */
  String type();

  record Text(String value) implements IrValue {
    public Text {
      Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String type() {
      return "text";
    }
  }

  record Number(BigDecimal value) implements IrValue {
    public Number {
      Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String type() {
      return "number";
    }
  }

  /** A measured amount such as {@code 85 %}, {@code 5 kg} or {@code 512 MiB}. */
  record Quantity(BigDecimal value, String unit) implements IrValue {
    private static final Pattern UNIT = Pattern.compile("^[^\\s]{1,16}$");

    public Quantity {
      Objects.requireNonNull(value, "value must not be null");
      Objects.requireNonNull(unit, "unit must not be null");
      if (!UNIT.matcher(unit).matches()) {
        throw new IllegalArgumentException("unit must be 1-16 non-space characters");
      }
    }

    @Override
    public String type() {
      return "quantity";
    }
  }

  /**
   * A monetary amount. {@code currency} is an ISO 4217 code only when it was justified by the text
   * or context; an ambiguous word such as "krona" leaves it empty rather than guessing.
   */
  record Money(BigDecimal amount, Optional<String> currency) implements IrValue {
    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public Money {
      Objects.requireNonNull(amount, "amount must not be null");
      currency = currency == null ? Optional.empty() : currency;
      if (currency.isPresent() && !ISO_4217.matcher(currency.get()).matches()) {
        throw new IllegalArgumentException("currency must be an ISO 4217 code");
      }
    }

    @Override
    public String type() {
      return "money";
    }
  }

  record Bool(boolean value) implements IrValue {
    @Override
    public String type() {
      return "boolean";
    }
  }

  /**
   * A point or window in time. {@code value} is ISO-8601: an instant, a local date, or a duration.
   * For {@link Kind#RELATIVE_PAST} the window ends at {@code reference}; without an explicit
   * reference the window stays unresolved instead of silently using "now".
   */
  record Temporal(Kind kind, String value, Optional<String> reference) implements IrValue {
    public enum Kind {
      INSTANT,
      DATE,
      DURATION,
      RELATIVE_PAST
    }

    public Temporal {
      Objects.requireNonNull(kind, "kind must not be null");
      Objects.requireNonNull(value, "value must not be null");
      reference = reference == null ? Optional.empty() : reference;
    }

    public boolean resolved() {
      return kind != Kind.RELATIVE_PAST || reference.isPresent();
    }

    @Override
    public String type() {
      return "temporal";
    }
  }

  /** A range; either bound may be absent, but not both. Bounds must share one value type. */
  record Range(
      Optional<IrValue> min, Optional<IrValue> max, boolean minInclusive, boolean maxInclusive)
      implements IrValue {
    public Range {
      min = min == null ? Optional.empty() : min;
      max = max == null ? Optional.empty() : max;
      if (min.isEmpty() && max.isEmpty()) {
        throw new IllegalArgumentException("a range needs at least one bound");
      }
    }

    @Override
    public String type() {
      return "range";
    }
  }

  record ListOf(List<IrValue> values) implements IrValue {
    public ListOf {
      values = List.copyOf(values);
    }

    @Override
    public String type() {
      return "list";
    }
  }
}
