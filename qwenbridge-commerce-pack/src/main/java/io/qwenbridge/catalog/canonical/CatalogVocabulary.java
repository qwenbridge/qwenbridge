package io.qwenbridge.catalog.canonical;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Category, brand and attribute vocabularies with aliases (QB-094). Matching is on a key: Unicode
 * NFKC, trimmed, whitespace collapsed and lower-cased. An alias that maps to two different
 * canonical terms is rejected when the vocabulary is built, so normalization is never order
 * dependent. The vocabulary only maps terms; it never discards the source value.
 */
public final class CatalogVocabulary {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");

  private final String version;
  private final Map<String, String> brands;
  private final Map<String, String> categories;
  private final Map<String, String> parents;
  private final Map<String, String> attributeNames;
  private final Map<String, Map<String, String>> attributeValues;
  private final Set<String> lowercased;
  private final Set<String> inferable;
  private final int longestAlias;

  private CatalogVocabulary(Builder b) {
    version = b.version;
    brands = Map.copyOf(b.brands);
    categories = Map.copyOf(b.categories);
    parents = Map.copyOf(b.parents);
    attributeNames = Map.copyOf(b.attributeNames);
    var values = new HashMap<String, Map<String, String>>();
    b.attributeValues.forEach((k, v) -> values.put(k, Map.copyOf(v)));
    attributeValues = Map.copyOf(values);
    lowercased = Set.copyOf(b.lowercased);
    inferable = Set.copyOf(b.inferable);
    int longest = 1;
    for (var aliases : attributeValues.values())
      for (String alias : aliases.keySet()) longest = Math.max(longest, tokens(alias).size());
    longestAlias = longest;
  }

  public static Builder builder(String version) {
    return new Builder(version);
  }

  /** The matching key of a term. */
  public static String key(String value) {
    return WHITESPACE
        .matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).strip())
        .replaceAll(" ")
        .toLowerCase(Locale.ROOT);
  }

  /** Word tokens of a text, keyed; letters, digits and combining marks in any script. */
  public static List<String> tokens(String text) {
    var out = new ArrayList<String>();
    for (String token : NON_WORD.split(key(text))) if (!token.isEmpty()) out.add(token);
    return out;
  }

  public String version() {
    return version;
  }

  public Optional<String> brand(String source) {
    return Optional.ofNullable(brands.get(key(source)));
  }

  public Optional<String> category(String source) {
    return Optional.ofNullable(categories.get(key(source)));
  }

  /** Root-first path of a known category id. */
  public List<String> categoryPath(String id) {
    var path = new ArrayList<String>();
    for (String at = id; at != null; at = parents.get(at)) path.addFirst(at);
    return List.copyOf(path);
  }

  public Optional<String> attributeName(String source) {
    return Optional.ofNullable(attributeNames.get(key(source)));
  }

  public Optional<String> attributeValue(String attribute, String source) {
    var values = attributeValues.get(attribute);
    return values == null ? Optional.empty() : Optional.ofNullable(values.get(key(source)));
  }

  /** Whether unrecognized values of this attribute are lower-cased (case carries no meaning). */
  public boolean lowercases(String attribute) {
    return lowercased.contains(attribute);
  }

  /** Attributes that may be inferred from the title when the source does not state them. */
  public Set<String> inferable() {
    return inferable;
  }

  /** Canonical values of {@code attribute} whose alias occurs as a whole-word phrase in text. */
  public SortedSet<String> mentions(String attribute, String text) {
    var values = attributeValues.getOrDefault(attribute, Map.of());
    var words = tokens(text);
    var found = new TreeSet<String>();
    for (int start = 0; start < words.size(); start++)
      for (int length = 1; length <= longestAlias && start + length <= words.size(); length++) {
        String canonical = values.get(String.join(" ", words.subList(start, start + length)));
        if (canonical != null) found.add(canonical);
      }
    return found;
  }

  public static final class Builder {
    private final String version;
    private final Map<String, String> brands = new HashMap<>();
    private final Map<String, String> categories = new HashMap<>();
    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, String> attributeNames = new HashMap<>();
    private final Map<String, Map<String, String>> attributeValues = new HashMap<>();
    private final Set<String> lowercased = new HashSet<>();
    private final Set<String> inferable = new HashSet<>();

    private Builder(String version) {
      if (version == null || version.isBlank())
        throw new IllegalArgumentException("Vocabulary version required");
      this.version = version;
    }

    public Builder brand(String canonical, String... aliases) {
      add(brands, "brand", canonical, canonical, aliases);
      return this;
    }

    /** A category; {@code parent} is {@code null} for a root. Ids are lower-case keys. */
    public Builder category(String id, String parent, String... aliases) {
      if (!id.equals(key(id))) throw new IllegalArgumentException("Category id must be a key");
      if (parents.containsKey(id) || categories.get(id) != null && categories.get(id).equals(id))
        throw new IllegalArgumentException("Duplicate category " + id);
      if (parent != null) parents.put(id, parent);
      add(categories, "category", id, id, aliases);
      return this;
    }

    public Builder attributeName(String canonical, String... aliases) {
      add(attributeNames, "attribute name", canonical, canonical, aliases);
      return this;
    }

    public Builder attributeValue(String attribute, String canonical, String... aliases) {
      add(
          attributeValues.computeIfAbsent(attribute, a -> new HashMap<>()),
          attribute + " value",
          canonical,
          canonical,
          aliases);
      return this;
    }

    public Builder lowercase(String... attributes) {
      lowercased.addAll(List.of(attributes));
      return this;
    }

    public Builder inferable(String... attributes) {
      inferable.addAll(List.of(attributes));
      return this;
    }

    private static void add(
        Map<String, String> target, String kind, String canonical, String first, String[] more) {
      var all = new LinkedHashSet<String>();
      all.add(first);
      all.addAll(List.of(more));
      for (String alias : all) {
        String previous = target.putIfAbsent(key(alias), canonical);
        if (previous != null && !previous.equals(canonical))
          throw new IllegalArgumentException(
              "Ambiguous " + kind + " alias '" + alias + "': " + previous + " and " + canonical);
      }
    }

    public CatalogVocabulary build() {
      for (var entry : parents.entrySet()) {
        if (!categories.containsKey(entry.getValue())
            || !categories.get(entry.getValue()).equals(entry.getValue()))
          throw new IllegalArgumentException("Unknown parent category " + entry.getValue());
        var seen = new HashSet<String>();
        for (String at = entry.getKey(); at != null; at = parents.get(at))
          if (!seen.add(at)) throw new IllegalArgumentException("Category cycle at " + at);
      }
      for (String attribute : inferable)
        if (!attributeValues.containsKey(attribute))
          throw new IllegalArgumentException("Inferable attribute needs values: " + attribute);
      return new CatalogVocabulary(this);
    }
  }

  /** Read-only view of the categories, for documentation and tests. */
  public Set<String> categoryIds() {
    return Collections.unmodifiableSet(new TreeSet<>(new HashSet<>(categories.values())));
  }
}
