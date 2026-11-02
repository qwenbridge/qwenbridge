package io.qwenbridge.core.intent;

import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.SubQuery;
import java.util.List;
import java.util.Objects;

/**
 * Result of intent analysis for one request (QB-022–QB-027): intents with evidence, retrieval
 * needs, decomposition and the assessment, all expressed in QueryIR types.
 *
 * @param taxonomyVersion version of the taxonomy the intent types belong to
 */
public record IntentUnderstanding(
    String taxonomyVersion,
    List<Intent> intents,
    RetrievalHints retrievalHints,
    List<SubQuery> subqueries,
    QueryAssessment assessment) {

  public IntentUnderstanding {
    Objects.requireNonNull(taxonomyVersion, "taxonomyVersion must not be null");
    intents = intents == null ? List.of() : List.copyOf(intents);
    retrievalHints = retrievalHints == null ? RetrievalHints.NONE : retrievalHints;
    subqueries = subqueries == null ? List.of() : List.copyOf(subqueries);
    Objects.requireNonNull(assessment, "assessment must not be null");
  }

  /** Copies intents, hints and subqueries into a QueryIR, keeping everything else. */
  public QueryIR applyTo(QueryIR ir) {
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        intents,
        ir.entities(),
        ir.constraints(),
        ir.semanticSignals(),
        retrievalHints,
        subqueries,
        ir.extensions());
  }
}
