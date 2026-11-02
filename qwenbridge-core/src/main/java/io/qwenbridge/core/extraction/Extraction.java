package io.qwenbridge.core.extraction;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Entity;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.SemanticSignal;
import java.util.List;

/** Output of extraction in QueryIR types, plus what could not be resolved (QB-029–QB-034). */
public record Extraction(
    List<Entity> entities,
    List<Constraint> constraints,
    List<SemanticSignal> semanticSignals,
    List<Unresolved> unresolved) {

  public Extraction {
    entities = entities == null ? List.of() : List.copyOf(entities);
    constraints = constraints == null ? List.of() : List.copyOf(constraints);
    semanticSignals = semanticSignals == null ? List.of() : List.copyOf(semanticSignals);
    unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
  }

  /** Copies entities, constraints and signals into a QueryIR, keeping everything else. */
  public QueryIR applyTo(QueryIR ir) {
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        entities,
        constraints,
        semanticSignals,
        ir.retrievalHints(),
        ir.subqueries(),
        ir.extensions());
  }
}
