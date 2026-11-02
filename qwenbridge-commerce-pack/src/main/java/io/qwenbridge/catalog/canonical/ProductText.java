package io.qwenbridge.catalog.canonical;

/**
 * The text a product is embedded from (QB-097).
 *
 * @param template the template id and version; a different template means a re-embedding
 * @param fingerprint SHA-256 over the template id and the text, lower-case hex
 */
public record ProductText(String template, String text, String fingerprint) {
  public ProductText {
    if (template == null || text == null || fingerprint == null || fingerprint.length() != 64)
      throw new IllegalArgumentException("Invalid product text");
  }
}
