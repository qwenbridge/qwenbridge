package io.qwenbridge.catalog.canonical;

/** A text field with its provenance. */
public record SourcedText(String value, Provenance provenance) {
  public SourcedText {
    if (value == null || provenance == null) throw new IllegalArgumentException("Invalid text");
  }
}
