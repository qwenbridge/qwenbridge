package io.qwenbridge.core.recovery;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One permitted way to relax a constraint on a field (QB-126).
 *
 * <ul>
 *   <li>RAISE_UPPER_BOUND: an LT/LTE bound is multiplied by {@code factor} in (1, 2].
 *   <li>LOWER_LOWER_BOUND: a GT/GTE bound is multiplied by {@code factor} in [0.5, 1).
 *   <li>DROP_EQUALITY: an EQ requirement is removed ({@code factor} unused, 1).
 * </ul>
 *
 * Exclusions (NE, NOT_IN, NOT_CONTAINS, NOT_EXISTS) have no relaxation kind: they are never
 * relaxed.
 */
public record RelaxationRule(String field, Kind kind, BigDecimal factor) {

  public enum Kind {
    RAISE_UPPER_BOUND,
    LOWER_LOWER_BOUND,
    DROP_EQUALITY
  }

  public RelaxationRule {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(factor, "factor must not be null");
    if (!field.matches("[A-Za-z_][A-Za-z0-9_.]{0,63}")) {
      throw new IllegalArgumentException("invalid field");
    }
    boolean valid =
        switch (kind) {
          case RAISE_UPPER_BOUND ->
              factor.compareTo(BigDecimal.ONE) > 0 && factor.compareTo(BigDecimal.TWO) <= 0;
          case LOWER_LOWER_BOUND ->
              factor.compareTo(new BigDecimal("0.5")) >= 0 && factor.compareTo(BigDecimal.ONE) < 0;
          case DROP_EQUALITY -> factor.compareTo(BigDecimal.ONE) == 0;
        };
    if (!valid) {
      throw new IllegalArgumentException("factor out of range for " + kind);
    }
  }

  public static RelaxationRule raise(String field, String factor) {
    return new RelaxationRule(field, Kind.RAISE_UPPER_BOUND, new BigDecimal(factor));
  }

  public static RelaxationRule lower(String field, String factor) {
    return new RelaxationRule(field, Kind.LOWER_LOWER_BOUND, new BigDecimal(factor));
  }

  public static RelaxationRule drop(String field) {
    return new RelaxationRule(field, Kind.DROP_EQUALITY, BigDecimal.ONE);
  }
}
