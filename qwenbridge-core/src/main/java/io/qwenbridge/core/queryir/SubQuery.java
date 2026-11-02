package io.qwenbridge.core.queryir;

import java.util.List;
import java.util.Objects;

/**
 * One step of a decomposed request (QB-025, QB-039). Subqueries reference intents and constraints
 * of the parent QueryIR by id, so the original request remains the single source of truth and stays
 * recoverable. {@code dependsOn} must form an acyclic graph.
 *
 * @param objective short description of what this step answers
 * @param inheritsParentConstraints whether whole-query constraints (no scope) also apply here
 */
public record SubQuery(
    String id,
    String objective,
    List<String> intentIds,
    List<String> constraintIds,
    List<String> dependsOn,
    boolean inheritsParentConstraints) {

  public SubQuery {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(objective, "objective must not be null");
    intentIds = intentIds == null ? List.of() : List.copyOf(intentIds);
    constraintIds = constraintIds == null ? List.of() : List.copyOf(constraintIds);
    dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
  }
}
