package io.qwenbridge.core.extraction;

import io.qwenbridge.core.queryir.Names;
import java.util.List;
import java.util.Objects;

/**
 * Domain vocabulary supplied by Domain Packs (QB-029): brands, materials, Kubernetes resource kinds
 * and so on. The Core extractor knows no domain words itself; new entity types need no Core change.
 * Aliases may be in any language and are matched on word boundaries, case-insensitively.
 *
 * @param terms dictionary terms; the {@code field} of a term is the schema field it constrains
 */
public record EntityDictionary(List<Term> terms) {

  public static final EntityDictionary EMPTY = new EntityDictionary(List.of());

  public EntityDictionary {
    terms = terms == null ? List.of() : List.copyOf(terms);
  }

  /**
   * @param entityType schema entity type, e.g. {@code brand}, {@code material}
   * @param field schema field constrained by this term, e.g. {@code brand}, {@code material}
   * @param canonical canonical value stored in QueryIR
   * @param aliases surface forms, including {@code canonical} itself if it can appear in text
   */
  public record Term(String entityType, String field, String canonical, List<String> aliases) {
    public Term {
      if (!Names.valid(entityType) || !Names.valid(field)) {
        throw new IllegalArgumentException("entity type and field must be valid names");
      }
      Objects.requireNonNull(canonical, "canonical must not be null");
      aliases = aliases == null || aliases.isEmpty() ? List.of(canonical) : List.copyOf(aliases);
    }
  }
}
