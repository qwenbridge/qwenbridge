package io.qwenbridge.core.spelling;

import io.qwenbridge.core.request.LanguageTag;
import java.lang.Character.UnicodeScript;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A local, language-specific correction capability (QB-291 F). Providers live outside the Core
 * (dictionaries, packs) and are discovered at run time; the Core bundles no language data. A
 * provider never calls a network service.
 */
public interface CorrectionProvider {

  enum Capability {
    /** Knows the words of a language and proposes dictionary words. */
    DICTIONARY,
    /** Domain vocabulary such as product and brand names. */
    DOMAIN_VOCABULARY,
    /** Knows adjacent word pairs, used for contextual ranking. */
    PHRASES,
    /** Word frequencies, used for ranking. */
    FREQUENCIES
  }

  /**
   * What a provider declares.
   *
   * @param requirements the dictionaries or models it needs, for operators
   * @param entries the number of loaded entries, for resource planning
   * @param approximateBytes approximate heap use
   */
  record Descriptor(
      String id,
      Set<LanguageTag> languages,
      Set<UnicodeScript> scripts,
      Set<Capability> capabilities,
      String requirements,
      long entries,
      long approximateBytes) {
    public Descriptor {
      Objects.requireNonNull(id, "id must not be null");
      languages = Set.copyOf(languages);
      scripts = Set.copyOf(scripts);
      capabilities = Set.copyOf(capabilities);
      Objects.requireNonNull(requirements, "requirements must not be null");
    }
  }

  /**
   * A correction candidate.
   *
   * @param form the word as it should be written (canonical casing)
   * @param distance weighted edit distance from the input
   * @param frequency 0..1 relative frequency in the provider's data (0 when unknown)
   * @param domain whether the word comes from domain vocabulary
   */
  record Candidate(
      String form, LanguageTag language, double distance, double frequency, boolean domain) {
    public Candidate {
      Objects.requireNonNull(form, "form must not be null");
      Objects.requireNonNull(language, "language must not be null");
    }
  }

  Descriptor descriptor();

  /**
   * Whether the word is correctly spelled in the language (case-insensitive where the script has
   * case).
   */
  boolean knows(LanguageTag language, String word);

  /** Candidates within the weighted distance, best first; never the word itself. */
  List<Candidate> candidates(LanguageTag language, String word, double maxDistance);

  /** Whether {@code first second} is a known adjacent pair in the language (context). */
  default boolean pair(LanguageTag language, String first, String second) {
    return false;
  }
}
