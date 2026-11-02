package io.qwenbridge.catalog.canonical;

import java.math.BigDecimal;

/** Weight in kilograms; a conversion from another unit is recorded as DERIVED provenance. */
public record Weight(BigDecimal kilograms, Provenance provenance) {
  public Weight {
    if (kilograms == null || provenance == null || kilograms.signum() < 0)
      throw new IllegalArgumentException("Negative weight");
  }
}
