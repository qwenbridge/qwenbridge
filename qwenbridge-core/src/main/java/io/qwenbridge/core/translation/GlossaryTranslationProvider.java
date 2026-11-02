package io.qwenbridge.core.translation;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.spelling.ProtectedTokens;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A local, offline translation provider driven by phrase tables (QB-286). It is language-neutral:
 * the Core ships no tables; an application installs one file per direction.
 *
 * <p>Format: {@code <source>_to_<target>.tsv} (for example {@code en_to_sv.tsv}, {@code
 * zh-Hans_to_en.tsv}), UTF-8, one entry per line: {@code source phrase<TAB>target phrase}; {@code
 * #} starts a comment. Matching is greedy longest-phrase (up to {@value #MAX_PHRASE_WORDS} words),
 * case-insensitive. Words a table does not know are kept as written; protected tokens (identifiers,
 * acronyms, names with inner capitals), numbers and punctuation are always copied unchanged.
 *
 * <p>This is phrase substitution, not machine translation: word order changes only through phrase
 * entries. The result reports {@link Confidence#heuristic()} and the share of translatable words
 * covered as {@code coverage} metadata, which the expansion engine compares with its minimum.
 * Output that translated nothing is not a translation and is reported as {@code INVALID_OUTPUT}.
 */
public final class GlossaryTranslationProvider implements TranslationProvider {

  public static final int MAX_PHRASE_WORDS = 6;
  static final int MAX_INPUT_CHARS = 2000;
  private static final String SUFFIX = ".tsv";
  private static final String SEPARATOR = "_to_";

  private final String id;
  private final Map<String, Map<String, String>> tables;
  private final TranslationCapabilities capabilities;
  private final int entries;
  private final Map<String, Set<String>> vocabulary;

  private GlossaryTranslationProvider(String id, Map<String, Map<String, String>> tables) {
    this.id = id;
    this.tables = Map.copyOf(tables);
    Set<String> sources = new LinkedHashSet<>();
    Set<String> targets = new LinkedHashSet<>();
    for (String pair : tables.keySet()) {
      int arrow = pair.indexOf('>');
      sources.add(pair.substring(0, arrow));
      targets.add(pair.substring(arrow + 1));
    }
    this.capabilities =
        new TranslationCapabilities(sources, targets, false, MAX_INPUT_CHARS, tables.keySet());
    this.entries = tables.values().stream().mapToInt(Map::size).sum();
    Map<String, Set<String>> words = new LinkedHashMap<>();
    tables.forEach(
        (pair, table) -> {
          Set<String> known =
              words.computeIfAbsent(pair.substring(0, pair.indexOf('>')), k -> new HashSet<>());
          for (String phrase : table.keySet()) known.addAll(List.of(phrase.split(" ")));
        });
    words.replaceAll((k, v) -> Set.copyOf(v));
    this.vocabulary = Map.copyOf(words);
  }

  /** Reads every {@code <source>_to_<target>.tsv} in the directory. */
  public static GlossaryTranslationProvider load(String id, Path directory) throws IOException {
    Map<String, Map<String, String>> tables = new LinkedHashMap<>();
    try (var files = Files.list(directory)) {
      for (Path file : files.sorted().toList()) {
        Path fileName = file.getFileName();
        String name = fileName == null ? "" : fileName.toString();
        if (!name.endsWith(SUFFIX)) continue;
        String stem = name.substring(0, name.length() - SUFFIX.length());
        int sep = stem.indexOf(SEPARATOR);
        if (sep < 1)
          throw new IllegalArgumentException("Glossary file must be <source>_to_<target>.tsv");
        LanguageTag source = tag(stem.substring(0, sep), name);
        LanguageTag target = tag(stem.substring(sep + SEPARATOR.length()), name);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
          tables.put(
              TranslationCapabilities.pair(source.value(), target.value()), parse(reader, name));
        }
      }
    }
    if (tables.isEmpty())
      throw new IllegalArgumentException("No <source>_to_<target>.tsv glossary in " + directory);
    return new GlossaryTranslationProvider(id, tables);
  }

  /** Builds a provider from readers keyed by {@code source>target} (tests and embedding). */
  public static GlossaryTranslationProvider of(String id, Map<String, Reader> tables)
      throws IOException {
    Map<String, Map<String, String>> parsed = new LinkedHashMap<>();
    for (var e : tables.entrySet()) {
      int arrow = e.getKey().indexOf('>');
      LanguageTag source = tag(e.getKey().substring(0, arrow), e.getKey());
      LanguageTag target = tag(e.getKey().substring(arrow + 1), e.getKey());
      parsed.put(
          TranslationCapabilities.pair(source.value(), target.value()),
          parse(e.getValue(), e.getKey()));
    }
    return new GlossaryTranslationProvider(id, parsed);
  }

  private static LanguageTag tag(String value, String file) {
    return LanguageTag.parse(value)
        .filter(t -> !t.isUndetermined())
        .orElseThrow(() -> new IllegalArgumentException("Not a language tag in " + file));
  }

  private static Map<String, String> parse(Reader source, String name) throws IOException {
    Map<String, String> table = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(source)) {
      String line;
      int number = 0;
      while ((line = reader.readLine()) != null) {
        number++;
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        int tab = line.indexOf('\t');
        if (tab < 0)
          throw new IllegalArgumentException(name + ":" + number + ": expected source<TAB>target");
        String from = key(line.substring(0, tab));
        String to = line.substring(tab + 1).strip();
        if (from.isEmpty() || to.isEmpty())
          throw new IllegalArgumentException(name + ":" + number + ": empty phrase");
        if (from.split(" ").length > MAX_PHRASE_WORDS)
          throw new IllegalArgumentException(name + ":" + number + ": phrase too long");
        table.putIfAbsent(from, to);
      }
    }
    return table;
  }

  private static String key(String phrase) {
    return phrase.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public TranslationCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public List<String> requirements() {
    return List.of("local phrase tables <source>_to_<target>.tsv; no network, no model");
  }

  @Override
  public Map<String, String> metadata() {
    return Map.of(
        "method",
        "phrase-table",
        "pairs",
        String.valueOf(tables.size()),
        "entries",
        String.valueOf(entries));
  }

  @Override
  public TranslationResult translate(TranslationRequest request) {
    Optional<Map<String, String>> table = table(request.source(), request.target());
    if (table.isEmpty())
      return TranslationResult.Failed.of(TranslationResult.Failure.UNSUPPORTED_LANGUAGE_PAIR, id);
    String text = request.text();
    List<ProtectedTokens.Token> tokens = ProtectedTokens.tokenize(text);
    StringBuilder out = new StringBuilder();
    int cursor = 0;
    int translated = 0;
    int unknown = 0;
    int i = 0;
    while (i < tokens.size()) {
      if (!translatable(tokens.get(i))) {
        i++;
        continue;
      }
      int matched = 0;
      String replacement = null;
      for (int n = Math.min(MAX_PHRASE_WORDS, tokens.size() - i); n >= 1; n--) {
        if (!run(text, tokens, i, n)) continue;
        String phrase = key(text.substring(tokens.get(i).start(), tokens.get(i + n - 1).end()));
        String hit = table.get().get(phrase);
        if (hit != null) {
          matched = n;
          replacement = hit;
          break;
        }
      }
      if (matched == 0) {
        unknown++;
        i++;
        continue;
      }
      out.append(text, cursor, tokens.get(i).start()).append(replacement);
      cursor = tokens.get(i + matched - 1).end();
      translated += matched;
      i += matched;
    }
    out.append(text, cursor, text.length());
    if (translated == 0)
      return TranslationResult.Failed.of(TranslationResult.Failure.INVALID_OUTPUT, id);
    double coverage = (double) translated / (translated + unknown);
    return new TranslationResult.Translated(
        out.toString(),
        request.source(),
        request.target(),
        id,
        Confidence.heuristic(),
        Map.of(
            "method",
            "phrase-table",
            "coverage",
            String.format(Locale.ROOT, "%.2f", coverage),
            "unknownWords",
            String.valueOf(unknown)));
  }

  @Override
  public Map<String, Double> vocabularyEvidence(String text) {
    List<String> words =
        ProtectedTokens.tokenize(text).stream()
            .filter(t -> t.kind() == ProtectedTokens.Kind.WORD)
            .map(t -> key(t.text()))
            .toList();
    if (words.isEmpty()) return Map.of();
    Map<String, Double> out = new LinkedHashMap<>();
    vocabulary.forEach(
        (language, known) ->
            out.put(
                language, (double) words.stream().filter(known::contains).count() / words.size()));
    return Map.copyOf(out);
  }

  private Optional<Map<String, String>> table(LanguageTag source, LanguageTag target) {
    for (String s : List.of(source.value(), source.primaryLanguage()))
      for (String t : List.of(target.value(), target.primaryLanguage())) {
        Map<String, String> table = tables.get(TranslationCapabilities.pair(s, t));
        if (table != null) return Optional.of(table);
      }
    return Optional.empty();
  }

  /** Words and short words are translatable; identifiers, acronyms and names are not. */
  private static boolean translatable(ProtectedTokens.Token token) {
    return token.kind() == ProtectedTokens.Kind.WORD || "short".equals(token.reason());
  }

  /** Whether tokens i..i+n-1 are translatable words separated only by whitespace. */
  private static boolean run(String text, List<ProtectedTokens.Token> tokens, int i, int n) {
    for (int k = i; k < i + n; k++) {
      if (!translatable(tokens.get(k))) return false;
      if (k > i && !text.substring(tokens.get(k - 1).end(), tokens.get(k).start()).isBlank())
        return false;
    }
    return true;
  }
}
