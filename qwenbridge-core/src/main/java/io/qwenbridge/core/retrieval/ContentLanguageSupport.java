package io.qwenbridge.core.retrieval;

import java.util.Set;

/**
 * Retrieval-provider side of cross-language retrieval (QB-020): which content languages a provider
 * has indexed and whether it can match meaning across languages by itself (for example with a
 * multilingual embedding model). Declared by retrieval connectors; Core never assumes it.
 *
 * @param indexedLanguages primary subtags of content languages the provider has indexed; empty
 *     means unknown
 * @param multilingualSemantic whether semantic matching works across languages without translating
 *     the query
 */
public record ContentLanguageSupport(Set<String> indexedLanguages, boolean multilingualSemantic) {

  public static final ContentLanguageSupport UNKNOWN = new ContentLanguageSupport(Set.of(), false);

  public ContentLanguageSupport {
    indexedLanguages = indexedLanguages == null ? Set.of() : Set.copyOf(indexedLanguages);
  }
}
