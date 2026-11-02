package io.qwenbridge.catalog.canonical;

/**
 * Availability and optional quantity. Contradictions are rejected: a positive quantity cannot be
 * out of stock, discontinued or on backorder, and zero cannot be in stock.
 */
public record Inventory(Availability availability, Integer quantity, Provenance provenance) {
  public Inventory {
    if (availability == null) throw new IllegalArgumentException("Contradictory inventory");
    if (availability != Availability.UNKNOWN && provenance == null)
      throw new IllegalArgumentException("Inventory provenance required");
    if (quantity != null) {
      boolean contradictory =
          quantity < 0
              || switch (availability) {
                case IN_STOCK -> quantity == 0;
                case OUT_OF_STOCK, DISCONTINUED, BACKORDER -> quantity > 0;
                case PREORDER, UNKNOWN -> false;
              };
      if (contradictory) throw new IllegalArgumentException("Contradictory inventory");
    }
  }
}
