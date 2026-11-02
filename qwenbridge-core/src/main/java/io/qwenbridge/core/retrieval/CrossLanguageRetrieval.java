package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationCapabilities;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides how a query reaches content in other languages (QB-020). The query side is {@link
 * RetrievalHints#crossLanguage()} and {@link RetrievalHints#contentLanguages()} in the QueryIR; the
 * provider side is {@link ContentLanguageSupport} and the translation provider's {@link
 * TranslationCapabilities}. The decision is deterministic and explains itself; it never silently
 * searches only the query's own language when other languages were asked for.
 */
public final class CrossLanguageRetrieval {

  private CrossLanguageRetrieval() {}

  public enum Strategy {
    /** Content is in the query language, or cross-language matching was not asked for. */
    NOT_NEEDED,
    /** The retrieval provider matches across languages itself. */
    MULTILINGUAL_SEMANTIC,
    /** Translate the query into each listed content language. */
    TRANSLATE_QUERY,
    /** Cross-language matching was asked for but nothing available can provide it. */
    UNSUPPORTED
  }

  /**
   * @param translateTo content languages to translate the query into (TRANSLATE_QUERY only)
   * @param reason stable, human-readable explanation
   */
  public record Decision(Strategy strategy, List<LanguageTag> translateTo, String reason) {
    public Decision {
      Objects.requireNonNull(strategy, "strategy must not be null");
      translateTo = translateTo == null ? List.of() : List.copyOf(translateTo);
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  /**
   * Retrieval hints for a query whose content languages are known: cross-language is wanted when no
   * content language matches the query's language.
   */
  public static RetrievalHints withContentLanguages(
      RetrievalHints hints, LanguageTag queryLanguage, List<LanguageTag> contentLanguages) {
    Objects.requireNonNull(hints, "hints must not be null");
    if (contentLanguages == null || contentLanguages.isEmpty()) {
      return hints;
    }
    boolean cross =
        queryLanguage.isUndetermined()
            || contentLanguages.stream()
                .noneMatch(c -> c.primaryLanguage().equals(queryLanguage.primaryLanguage()));
    return new RetrievalHints(hints.modes(), cross, contentLanguages);
  }

  public static Decision decide(
      LanguageTag queryLanguage,
      RetrievalHints hints,
      ContentLanguageSupport content,
      Optional<TranslationCapabilities> translation) {
    Objects.requireNonNull(queryLanguage, "queryLanguage must not be null");
    Objects.requireNonNull(hints, "hints must not be null");
    Objects.requireNonNull(content, "content must not be null");
    if (!hints.crossLanguage()) {
      return new Decision(Strategy.NOT_NEEDED, List.of(), "cross-language matching not requested");
    }
    Set<LanguageTag> others = new LinkedHashSet<>();
    for (LanguageTag c : hints.contentLanguages()) {
      if (!c.primaryLanguage().equals(queryLanguage.primaryLanguage())) {
        others.add(c);
      }
    }
    if (!hints.contentLanguages().isEmpty() && others.isEmpty()) {
      return new Decision(Strategy.NOT_NEEDED, List.of(), "content is in the query language");
    }
    if (content.multilingualSemantic()) {
      return new Decision(
          Strategy.MULTILINGUAL_SEMANTIC, List.of(), "provider matches across languages");
    }
    if (queryLanguage.isUndetermined()) {
      return new Decision(
          Strategy.UNSUPPORTED, List.of(), "query language undetermined; nothing is guessed");
    }
    if (others.isEmpty()) {
      return new Decision(
          Strategy.UNSUPPORTED, List.of(), "content languages unknown; no translation target");
    }
    if (translation.isEmpty()) {
      return new Decision(Strategy.UNSUPPORTED, List.of(), "no translation provider");
    }
    TranslationCapabilities caps = translation.get();
    List<LanguageTag> targets = new ArrayList<>();
    for (LanguageTag target : others) {
      if (caps.supportsSource(queryLanguage) && caps.supportsTarget(target)) {
        targets.add(target);
      }
    }
    if (targets.size() < others.size()) {
      return new Decision(
          Strategy.UNSUPPORTED, List.of(), "translation provider lacks a needed language pair");
    }
    return new Decision(Strategy.TRANSLATE_QUERY, targets, "translate query to content languages");
  }
}
