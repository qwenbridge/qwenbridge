package io.qwenbridge.catalog.canonical;

/** Canonical stock state of a variant (QB-095). */
public enum Availability {
  IN_STOCK,
  OUT_OF_STOCK,
  PREORDER,
  BACKORDER,
  DISCONTINUED,
  /** The source did not say; never assumed to be in or out of stock. */
  UNKNOWN
}
