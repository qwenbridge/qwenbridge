package io.qwenbridge.core.spelling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.Character.UnicodeScript;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An immutable word list for one language or for domain vocabulary (QB-291). Text format, UTF-8:
 * one entry per line, {@code word} or {@code word<TAB>frequency}; a line with several words is a
 * phrase whose words and adjacent pairs are all registered; {@code #} starts a comment. Lookups are
 * NFC-normalized and case-insensitive; the first form seen is kept as the canonical spelling (so
 * {@code iPhone} keeps its casing).
 */
public final class Lexicon {

  /** One word. */
  public record Entry(String form, String key, int length, long frequency) {}

  private final Map<String, Entry> entries;
  private final Map<Integer, List<Entry>> byLength;
  private final Set<String> pairs;
  private final Set<UnicodeScript> scripts;
  private final long maxFrequency;
  private final long characters;

  private Lexicon(Map<String, Entry> entries, Set<String> pairs) {
    this.entries = Map.copyOf(entries);
    this.pairs = Set.copyOf(pairs);
    Map<Integer, List<Entry>> lengths = new HashMap<>();
    EnumSet<UnicodeScript> found = EnumSet.noneOf(UnicodeScript.class);
    long max = 0;
    long chars = 0;
    for (Entry e : entries.values()) {
      lengths.computeIfAbsent(e.length(), k -> new ArrayList<>()).add(e);
      e.key()
          .codePoints()
          .filter(Character::isLetter)
          .mapToObj(UnicodeScript::of)
          .forEach(found::add);
      max = Math.max(max, e.frequency());
      chars += e.key().length();
    }
    Map<Integer, List<Entry>> frozen = new HashMap<>();
    lengths.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
    this.byLength = Collections.unmodifiableMap(frozen);
    this.scripts = Set.copyOf(found);
    this.maxFrequency = max;
    this.characters = chars;
  }

  public static String key(String word) {
    return Normalizer.normalize(word, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  public static Lexicon parse(Reader source) throws IOException {
    Map<String, Entry> entries = new HashMap<>();
    Set<String> pairs = new HashSet<>();
    try (BufferedReader reader = new BufferedReader(source)) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = Normalizer.normalize(line.strip(), Normalizer.Form.NFC);
        if (line.isEmpty() || line.startsWith("#")) continue;
        long frequency = 0;
        int tab = line.lastIndexOf('\t');
        if (tab > 0) {
          try {
            frequency = Math.max(0, Long.parseLong(line.substring(tab + 1).strip()));
            line = line.substring(0, tab).strip();
          } catch (NumberFormatException ignored) {
            // A tab inside a phrase line: no frequency.
          }
        }
        String[] words = line.split("\\s+");
        for (String word : words) {
          if (word.isEmpty() || word.codePoints().noneMatch(Character::isLetter)) continue;
          String key = key(word);
          long f = frequency;
          entries.merge(
              key,
              new Entry(word, key, word.codePointCount(0, word.length()), f),
              (old, add) ->
                  new Entry(
                      old.form(), key, old.length(), Math.max(old.frequency(), add.frequency())));
        }
        for (int i = 0; i + 1 < words.length; i++)
          pairs.add(key(words[i]) + " " + key(words[i + 1]));
      }
    }
    return new Lexicon(entries, pairs);
  }

  public boolean contains(String word) {
    return entries.containsKey(key(word));
  }

  public boolean pair(String first, String second) {
    return pairs.contains(key(first) + " " + key(second));
  }

  /** 0..1 relative frequency (log scale); 0 without frequencies. */
  public double frequency(Entry entry) {
    return maxFrequency <= 0 ? 0 : Math.log1p(entry.frequency()) / Math.log1p(maxFrequency);
  }

  /** Entries whose length is within {@code maxDistance} of {@code length}. */
  public List<Entry> near(int length, double maxDistance) {
    List<Entry> out = new ArrayList<>();
    int spread = (int) Math.floor(maxDistance);
    for (int l = Math.max(1, length - spread); l <= length + spread; l++)
      out.addAll(byLength.getOrDefault(l, List.of()));
    return out;
  }

  public Set<UnicodeScript> scripts() {
    return scripts;
  }

  public int size() {
    return entries.size();
  }

  public boolean hasFrequencies() {
    return maxFrequency > 0;
  }

  public boolean hasPairs() {
    return !pairs.isEmpty();
  }

  /** A rough heap estimate for operators. */
  public long approximateBytes() {
    return characters * 4 + entries.size() * 96L + pairs.size() * 64L;
  }
}
