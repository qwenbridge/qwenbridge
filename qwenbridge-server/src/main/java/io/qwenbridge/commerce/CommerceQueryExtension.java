package io.qwenbridge.commerce;

import io.qwenbridge.core.queryir.Extension;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Commerce payload of a universal QueryIR (QB-150), under the {@code commerce} namespace. Core
 * carries it without interpreting it; Commerce writes and reads it through this type, which checks
 * the version and the exact shape.
 *
 * @param schema the Commerce schema the query was interpreted against, {@code id@version}
 * @param catalogLanguage the language catalog text is matched in
 * @param currencies the currencies the query fixes (sorted, distinct); prices are never converted
 */
public record CommerceQueryExtension(
    String schema, String catalogLanguage, List<String> currencies) {
  public static final String NAMESPACE = "commerce";
  public static final String VERSION = "1.0";
  private static final Set<String> KEYS = Set.of("schema", "catalogLanguage", "currencies");

  public CommerceQueryExtension {
    Objects.requireNonNull(schema, "schema must not be null");
    Objects.requireNonNull(catalogLanguage, "catalogLanguage must not be null");
    if (!schema.matches("[a-z][a-z0-9-]{0,63}@[0-9]{1,6}"))
      throw new IllegalArgumentException("invalid schema reference");
    if (!catalogLanguage.matches("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"))
      throw new IllegalArgumentException("invalid catalog language");
    currencies =
        currencies == null
            ? List.of()
            : List.copyOf(currencies.stream().distinct().sorted().toList());
    for (String c : currencies)
      if (!c.matches("[A-Z]{3}")) throw new IllegalArgumentException("invalid currency");
  }

  public Extension toExtension() {
    return new Extension(
        VERSION,
        Map.of("schema", schema, "catalogLanguage", catalogLanguage, "currencies", currencies));
  }

  /** Reads the payload; anything but version 1.0 with exactly these typed keys is refused. */
  public static CommerceQueryExtension from(Extension extension) {
    if (!VERSION.equals(extension.version()))
      throw new IllegalArgumentException("unsupported commerce extension " + extension.version());
    if (!extension.data().keySet().equals(KEYS))
      throw new IllegalArgumentException("commerce extension keys must be " + KEYS);
    if (!(extension.data().get("schema") instanceof String schema)
        || !(extension.data().get("catalogLanguage") instanceof String language)
        || !(extension.data().get("currencies") instanceof List<?> list)
        || !list.stream().allMatch(String.class::isInstance))
      throw new IllegalArgumentException("commerce extension values have the wrong type");
    return new CommerceQueryExtension(
        schema, language, list.stream().map(String.class::cast).toList());
  }
}
