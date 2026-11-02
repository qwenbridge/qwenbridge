package io.qwenbridge.core.queryir;

import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The universal, typed, versioned intermediate representation of a user query (QB-036).
 *
 * <p>It is domain-neutral: Commerce, DevOps, Security and Observability queries all use this one
 * contract. Domain meaning comes from schema-defined entity types and field names and from {@link
 * #extensions()}; no Core field is specific to any domain. It is backend-neutral: retrieval needs
 * are expressed as {@link RetrievalHints}, never as search-engine, SQL or document-store syntax.
 *
 * <p>Structural invariants are enforced here; semantic rules (spans match the text, references
 * resolve, operators fit values, the subquery graph is acyclic) by {@link QueryIrValidator}. Use
 * {@link QueryIrJson} to (de)serialize.
 */
public record QueryIR(
    IrVersion version,
    Optional<String> queryId,
    String originalText,
    LanguageTag language,
    List<Intent> intents,
    List<Entity> entities,
    List<Constraint> constraints,
    List<SemanticSignal> semanticSignals,
    RetrievalHints retrievalHints,
    List<SubQuery> subqueries,
    Map<String, Extension> extensions,
    Optional<TextCorrection> correction,
    Optional<QueryExpansion> expansion) {

  /** A QueryIR without multilingual expansion metadata. */
  public QueryIR(
      IrVersion version,
      Optional<String> queryId,
      String originalText,
      LanguageTag language,
      List<Intent> intents,
      List<Entity> entities,
      List<Constraint> constraints,
      List<SemanticSignal> semanticSignals,
      RetrievalHints retrievalHints,
      List<SubQuery> subqueries,
      Map<String, Extension> extensions,
      Optional<TextCorrection> correction) {
    this(
        version,
        queryId,
        originalText,
        language,
        intents,
        entities,
        constraints,
        semanticSignals,
        retrievalHints,
        subqueries,
        extensions,
        correction,
        Optional.empty());
  }

  /** A QueryIR without typo correction or expansion metadata (every 1.0 document). */
  public QueryIR(
      IrVersion version,
      Optional<String> queryId,
      String originalText,
      LanguageTag language,
      List<Intent> intents,
      List<Entity> entities,
      List<Constraint> constraints,
      List<SemanticSignal> semanticSignals,
      RetrievalHints retrievalHints,
      List<SubQuery> subqueries,
      Map<String, Extension> extensions) {
    this(
        version,
        queryId,
        originalText,
        language,
        intents,
        entities,
        constraints,
        semanticSignals,
        retrievalHints,
        subqueries,
        extensions,
        Optional.empty(),
        Optional.empty());
  }

  public QueryIR {
    Objects.requireNonNull(version, "version must not be null");
    queryId = queryId == null ? Optional.empty() : queryId;
    Objects.requireNonNull(originalText, "originalText must not be null");
    Objects.requireNonNull(language, "language must not be null");
    intents = intents == null ? List.of() : List.copyOf(intents);
    entities = entities == null ? List.of() : List.copyOf(entities);
    constraints = constraints == null ? List.of() : List.copyOf(constraints);
    semanticSignals = semanticSignals == null ? List.of() : List.copyOf(semanticSignals);
    retrievalHints = retrievalHints == null ? RetrievalHints.NONE : retrievalHints;
    subqueries = subqueries == null ? List.of() : List.copyOf(subqueries);
    extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    correction = correction == null ? Optional.empty() : correction;
    if (correction.isPresent() && version.compareTo(IrVersion.V1_1) < 0)
      throw new IllegalArgumentException("the correction field needs QueryIR 1.1");
    expansion = expansion == null ? Optional.empty() : expansion;
    if (expansion.isPresent() && version.compareTo(IrVersion.V1_1) < 0)
      throw new IllegalArgumentException("the expansion field needs QueryIR 1.1");
  }

  /** A minimal QueryIR for a text with nothing extracted yet. */
  public static QueryIR of(String originalText, LanguageTag language) {
    return new QueryIR(
        IrVersion.CURRENT,
        Optional.empty(),
        originalText,
        language,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        RetrievalHints.NONE,
        List.of(),
        Map.of());
  }

  /** This QueryIR with typo correction metadata; the version becomes at least 1.1. */
  public QueryIR withCorrection(TextCorrection textCorrection) {
    return new QueryIR(
        version.compareTo(IrVersion.V1_1) < 0 ? IrVersion.V1_1 : version,
        queryId,
        originalText,
        language,
        intents,
        entities,
        constraints,
        semanticSignals,
        retrievalHints,
        subqueries,
        extensions,
        Optional.of(textCorrection),
        expansion);
  }

  /**
   * This QueryIR with multilingual expansion metadata (QB-286); the version becomes at least 1.1.
   */
  public QueryIR withExpansion(QueryExpansion queryExpansion) {
    return new QueryIR(
        version.compareTo(IrVersion.V1_1) < 0 ? IrVersion.V1_1 : version,
        queryId,
        originalText,
        language,
        intents,
        entities,
        constraints,
        semanticSignals,
        retrievalHints,
        subqueries,
        extensions,
        correction,
        Optional.of(queryExpansion));
  }

  public List<Constraint> hardConstraints() {
    return constraints.stream().filter(c -> c.strength() == Constraint.Strength.HARD).toList();
  }

  public List<Constraint> negativeConstraints() {
    return constraints.stream().filter(Constraint::negative).toList();
  }
}
