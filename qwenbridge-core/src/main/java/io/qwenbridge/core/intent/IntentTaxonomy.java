package io.qwenbridge.core.intent;

import io.qwenbridge.core.queryir.Names;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Versioned, domain-neutral intent taxonomy (QB-022).
 *
 * <p>Core intents and their boundaries:
 *
 * <ul>
 *   <li>{@code search}: find items, records or documents that satisfy a description.
 *   <li>{@code compare}: contrast two or more known items or groups on their properties.
 *   <li>{@code analyze}: compute, rank or classify over a set ("which IPs are most suspicious").
 *   <li>{@code explain}: ask why or how something is the way it is; no item retrieval implied.
 *   <li>{@code investigate}: diagnose a problem across signals or time (troubleshooting).
 *   <li>{@code refine}: change a previous request ("cheaper", "instead", "only black").
 *   <li>{@code navigate}: go to a place in an application rather than retrieve content.
 * </ul>
 *
 * Domain packs extend the taxonomy with namespaced intents ({@code commerce.reorder}) that always
 * name a core parent, so planners that only know core intents still behave sensibly.
 */
public final class IntentTaxonomy {

  public static final String VERSION = "1.0";

  public static final String SEARCH = "search";
  public static final String COMPARE = "compare";
  public static final String ANALYZE = "analyze";
  public static final String EXPLAIN = "explain";
  public static final String INVESTIGATE = "investigate";
  public static final String REFINE = "refine";
  public static final String NAVIGATE = "navigate";

  private static final Map<String, String> CORE =
      Map.of(
          SEARCH, "find items that satisfy a description",
          COMPARE, "contrast items on their properties",
          ANALYZE, "compute, rank or classify over a set",
          EXPLAIN, "explain why or how",
          INVESTIGATE, "diagnose a problem across signals or time",
          REFINE, "change a previous request",
          NAVIGATE, "go to a place in an application");

  private static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");

  private final Map<String, String> extensionParents;

  private IntentTaxonomy(Map<String, String> extensionParents) {
    this.extensionParents = Map.copyOf(extensionParents);
  }

  public static IntentTaxonomy core() {
    return new IntentTaxonomy(Map.of());
  }

  /**
   * Returns a taxonomy that additionally knows {@code namespace.name}, a child of {@code parent}.
   */
  public IntentTaxonomy withExtension(String namespace, String name, String parent) {
    if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
      throw new IllegalArgumentException("invalid intent namespace");
    }
    if (!Names.valid(name) || name.contains(".")) {
      throw new IllegalArgumentException("invalid intent name");
    }
    if (!CORE.containsKey(parent)) {
      throw new IllegalArgumentException("an extension intent must have a core parent");
    }
    String type = namespace + "." + name;
    if (CORE.containsKey(type) || extensionParents.containsKey(type)) {
      throw new IllegalArgumentException("intent already defined");
    }
    Map<String, String> next = new LinkedHashMap<>(extensionParents);
    next.put(type, parent);
    return new IntentTaxonomy(next);
  }

  public boolean isCore(String type) {
    return CORE.containsKey(type);
  }

  public boolean isKnown(String type) {
    return CORE.containsKey(type) || extensionParents.containsKey(type);
  }

  /** The core intent a (possibly extended) intent type belongs to. */
  public Optional<String> coreOf(String type) {
    if (CORE.containsKey(type)) {
      return Optional.of(type);
    }
    return Optional.ofNullable(extensionParents.get(type));
  }

  public Optional<String> definition(String type) {
    return coreOf(type).map(CORE::get);
  }

  public Map<String, String> coreIntents() {
    return CORE;
  }

  /**
   * Maps the legacy v1 intent names to the taxonomy. {@code PRODUCT_SEARCH} keeps its domain only
   * as a hint; {@code UNKNOWN} has no intent.
   */
  public static Optional<String> fromLegacy(String legacy) {
    if (legacy == null) {
      return Optional.empty();
    }
    return switch (legacy) {
      case "PRODUCT_SEARCH" -> Optional.of(SEARCH);
      case "COMPARE" -> Optional.of(COMPARE);
      case "FILTER" -> Optional.of(REFINE);
      case "NAVIGATION" -> Optional.of(NAVIGATE);
      default -> Optional.empty();
    };
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof IntentTaxonomy t && t.extensionParents.equals(extensionParents);
  }

  @Override
  public int hashCode() {
    return Objects.hash(extensionParents);
  }
}
