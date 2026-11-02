package io.qwenbridge.core.spelling;

import io.qwenbridge.core.request.LanguageTag;
import java.io.IOException;
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A language-neutral correction provider over word lists (QB-291). It supports exactly the
 * languages it has a lexicon for; the Core ships none. An optional domain vocabulary (product,
 * brand and model names) applies to every language whose script it shares.
 *
 * <p>{@link #load(String, Path)} reads a directory: one {@code <bcp47>.txt} per language and an
 * optional {@code domain.txt}. Files whose name is not a valid language tag are refused, so a
 * misnamed file never becomes a language.
 */
public final class DictionaryCorrectionProvider implements CorrectionProvider {

  private final String id;
  private final Map<LanguageTag, Lexicon> languages;
  private final Optional<Lexicon> domain;
  private final Descriptor descriptor;

  public DictionaryCorrectionProvider(
      String id, Map<LanguageTag, Lexicon> languages, Optional<Lexicon> domain) {
    this.id = id;
    this.languages = Map.copyOf(languages);
    this.domain = domain == null ? Optional.empty() : domain;
    EnumSet<UnicodeScript> scripts = EnumSet.noneOf(UnicodeScript.class);
    EnumSet<Capability> capabilities = EnumSet.of(Capability.DICTIONARY);
    long entries = 0;
    long bytes = 0;
    for (Lexicon l : this.languages.values()) {
      scripts.addAll(l.scripts());
      if (l.hasFrequencies()) capabilities.add(Capability.FREQUENCIES);
      if (l.hasPairs()) capabilities.add(Capability.PHRASES);
      entries += l.size();
      bytes += l.approximateBytes();
    }
    if (this.domain.isPresent()) {
      capabilities.add(Capability.DOMAIN_VOCABULARY);
      if (this.domain.get().hasPairs()) capabilities.add(Capability.PHRASES);
      entries += this.domain.get().size();
      bytes += this.domain.get().approximateBytes();
    }
    this.descriptor =
        new Descriptor(
            id,
            this.languages.keySet(),
            scripts,
            capabilities,
            "one UTF-8 word list per language (<bcp47>.txt), optional domain.txt; no network",
            entries,
            bytes);
  }

  public static DictionaryCorrectionProvider load(String id, Path directory) throws IOException {
    Map<LanguageTag, Lexicon> languages = new TreeMap<>(Comparator.comparing(LanguageTag::value));
    Optional<Lexicon> domain = Optional.empty();
    try (var files = Files.list(directory)) {
      for (Path file : files.filter(p -> fileName(p).endsWith(".txt")).sorted().toList()) {
        String name = fileName(file);
        String stem = name.substring(0, name.length() - 4);
        if (stem.equals("domain")) {
          domain = Optional.of(read(file));
          continue;
        }
        LanguageTag tag =
            LanguageTag.parse(stem)
                .filter(t -> !t.isUndetermined())
                .orElseThrow(() -> new IllegalArgumentException("Not a language tag: " + name));
        languages.put(tag, read(file));
      }
    }
    return new DictionaryCorrectionProvider(id, languages, domain);
  }

  private static String fileName(Path path) {
    Path name = path.getFileName();
    return name == null ? "" : name.toString();
  }

  private static Lexicon read(Path file) throws IOException {
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return Lexicon.parse(reader);
    }
  }

  @Override
  public Descriptor descriptor() {
    return descriptor;
  }

  private Optional<Lexicon> lexicon(LanguageTag language) {
    return Optional.ofNullable(languages.get(language));
  }

  private boolean domainApplies(LanguageTag language, String word) {
    if (domain.isEmpty() || !languages.containsKey(language)) return false;
    Set<UnicodeScript> wordScripts = EnumSet.noneOf(UnicodeScript.class);
    word.codePoints()
        .filter(Character::isLetter)
        .mapToObj(UnicodeScript::of)
        .forEach(wordScripts::add);
    return domain.get().scripts().containsAll(wordScripts);
  }

  @Override
  public boolean knows(LanguageTag language, String word) {
    return lexicon(language).map(l -> l.contains(word)).orElse(false)
        || (domainApplies(language, word) && domain.get().contains(word));
  }

  @Override
  public List<Candidate> candidates(LanguageTag language, String word, double maxDistance) {
    List<Candidate> out = new ArrayList<>();
    int length = word.codePointCount(0, word.length());
    lexicon(language).ifPresent(l -> collect(l, language, word, length, maxDistance, false, out));
    if (domainApplies(language, word))
      collect(domain.get(), language, word, length, maxDistance, true, out);
    out.sort(
        Comparator.comparingDouble(Candidate::distance)
            .thenComparing(c -> -c.frequency())
            .thenComparing(Candidate::form));
    return out;
  }

  private static void collect(
      Lexicon lexicon,
      LanguageTag language,
      String word,
      int length,
      double maxDistance,
      boolean domain,
      List<Candidate> out) {
    String key = Lexicon.key(word);
    for (Lexicon.Entry e : lexicon.near(length, maxDistance)) {
      if (e.key().equals(key)) continue;
      double d = EditDistance.between(key, e.key(), maxDistance);
      if (d <= maxDistance)
        out.add(new Candidate(e.form(), language, d, lexicon.frequency(e), domain));
    }
  }

  @Override
  public boolean pair(LanguageTag language, String first, String second) {
    return lexicon(language).map(l -> l.pair(first, second)).orElse(false)
        || domain.map(d -> d.pair(first, second)).orElse(false);
  }

  public Map<LanguageTag, Integer> sizes() {
    Map<LanguageTag, Integer> out = new HashMap<>();
    languages.forEach((k, v) -> out.put(k, v.size()));
    return out;
  }
}
