package io.qwenbridge.core.translation;

import io.qwenbridge.core.request.LanguageTag;
import java.util.Set;

/**
 * Capability discovery for a {@link TranslationProvider} (QB-015). Languages are primary subtags
 * ({@code en}, {@code sv}, {@code fa}); {@link #ANY} accepts every language. {@code mul} as a
 * source means code-switched text in several languages.
 *
 * @param sources source languages the provider accepts
 * @param targets target languages the provider produces
 * @param detectsSource whether an undetermined ({@code und}) source may be sent; the provider must
 *     then report the language it detected
 * @param maxInputChars largest accepted input in UTF-16 units
 * @param pairs the supported language pairs as {@code source>target} ({@link #pair}); empty means
 *     every combination of {@code sources} and {@code targets} (QB-286). A provider that serves
 *     only some combinations (for example en&gt;sv and sv&gt;en but not fa&gt;sv) must list them.
 */
public record TranslationCapabilities(
    Set<String> sources,
    Set<String> targets,
    boolean detectsSource,
    int maxInputChars,
    Set<String> pairs) {

  public static final String ANY = "*";

  /** Capabilities serving every combination of sources and targets. */
  public TranslationCapabilities(
      Set<String> sources, Set<String> targets, boolean detectsSource, int maxInputChars) {
    this(sources, targets, detectsSource, maxInputChars, Set.of());
  }

  public TranslationCapabilities {
    sources = Set.copyOf(sources);
    targets = Set.copyOf(targets);
    pairs = pairs == null ? Set.of() : Set.copyOf(pairs);
    for (String pair : pairs) {
      int arrow = pair.indexOf('>');
      if (arrow < 1 || arrow == pair.length() - 1 || pair.indexOf('>', arrow + 1) >= 0) {
        throw new IllegalArgumentException("a language pair is written source>target");
      }
    }
    if (sources.isEmpty() || targets.isEmpty()) {
      throw new IllegalArgumentException("a provider must declare source and target languages");
    }
    if (maxInputChars < 1) {
      throw new IllegalArgumentException("maxInputChars must be positive");
    }
  }

  /** Accepts any language pair, without source detection. */
  public static TranslationCapabilities anyToAny(int maxInputChars) {
    return new TranslationCapabilities(Set.of(ANY), Set.of(ANY), false, maxInputChars);
  }

  /** The key of a language pair in {@link #pairs()}. */
  public static String pair(String source, String target) {
    return source + ">" + target;
  }

  public boolean supportsSource(LanguageTag source) {
    if (source.isUndetermined()) {
      return detectsSource;
    }
    return sources.contains(ANY)
        || sources.contains(source.primaryLanguage())
        || sources.contains(source.value());
  }

  public boolean supportsTarget(LanguageTag target) {
    return !target.isUndetermined()
        && !"mul".equals(target.primaryLanguage())
        && (targets.contains(ANY)
            || targets.contains(target.primaryLanguage())
            || targets.contains(target.value()));
  }

  /**
   * Whether this exact direction is served (QB-286): source and target are supported and, when
   * pairs are declared, the pair is one of them. An undetermined source is decided by {@link
   * #detectsSource()} alone.
   */
  public boolean supportsPair(LanguageTag source, LanguageTag target) {
    if (!supportsSource(source) || !supportsTarget(target)) {
      return false;
    }
    if (pairs.isEmpty() || source.isUndetermined()) {
      return true;
    }
    for (String s : java.util.List.of(source.value(), source.primaryLanguage(), ANY)) {
      for (String t : java.util.List.of(target.value(), target.primaryLanguage(), ANY)) {
        if (pairs.contains(pair(s, t))) {
          return true;
        }
      }
    }
    return false;
  }
}
