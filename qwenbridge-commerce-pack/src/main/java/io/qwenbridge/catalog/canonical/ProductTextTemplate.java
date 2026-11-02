package io.qwenbridge.catalog.canonical;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic, versioned product-to-text rendering for embeddings (QB-097).
 *
 * <p>Version 1 renders, one labelled line each and only when present:
 *
 * <pre>
 * title: …
 * brand: …
 * category: root &gt; … &gt; leaf
 * attributes: name=value; …          (asserted product attributes, sorted by name)
 * options: name=v1|v2; …             (distinct asserted variant options, sorted)
 * description: …                     (first 1000 code points)
 * </pre>
 *
 * <p>It leaves out price, currency, stock, quantities, identifiers and provenance: they change
 * often, are matched exactly by filters, and would force a re-embedding on every stock update.
 * Inferred and ambiguous attributes are left out because they come from the title, which is already
 * there. The same product always renders to the same text and fingerprint; any change to these
 * rules requires a new version.
 */
public final class ProductTextTemplate {
  public static final String V1 = "commerce-product-text/1";
  static final int DESCRIPTION_CODE_POINTS = 1000;

  private ProductTextTemplate() {}

  public static ProductText render(CanonicalProduct product) {
    var text = new StringBuilder();
    line(text, "title", product.title().value());
    if (product.brand() != null) line(text, "brand", product.brand().value());
    if (product.category() != null)
      line(text, "category", String.join(" > ", product.category().path()));
    var attributes = new TreeMap<String, String>();
    product
        .attributes()
        .forEach(
            (name, value) -> {
              if (value.asserted()) attributes.put(name, value.value());
            });
    if (!attributes.isEmpty()) line(text, "attributes", join(attributes));
    var options = new TreeMap<String, TreeSet<String>>();
    for (var variant : product.variants())
      variant
          .options()
          .forEach(
              (name, value) -> {
                if (value.asserted())
                  options.computeIfAbsent(name, n -> new TreeSet<>()).add(value.value());
              });
    if (!options.isEmpty()) {
      var joined = new TreeMap<String, String>();
      options.forEach((name, values) -> joined.put(name, String.join("|", values)));
      line(text, "options", join(joined));
    }
    if (product.description() != null) {
      String description = product.description().value();
      if (description.codePointCount(0, description.length()) > DESCRIPTION_CODE_POINTS)
        description =
            description.substring(0, description.offsetByCodePoints(0, DESCRIPTION_CODE_POINTS));
      line(text, "description", description);
    }
    String rendered = text.toString();
    return new ProductText(V1, rendered, fingerprint(V1, rendered));
  }

  private static String join(Map<String, String> values) {
    var out = new StringBuilder();
    values.forEach(
        (name, value) -> {
          if (!out.isEmpty()) out.append("; ");
          out.append(name).append('=').append(value);
        });
    return out.toString();
  }

  private static void line(StringBuilder out, String label, String value) {
    if (!out.isEmpty()) out.append('\n');
    out.append(label).append(": ").append(value.replace('\n', ' '));
  }

  static String fingerprint(String template, String text) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(template.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
