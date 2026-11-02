package io.qwenbridge.model;

import io.qwenbridge.core.intent.IntentUnderstanding;
import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.SubQuery;
import java.util.List;

/**
 * Domain-neutral intent analysis in v1 responses (QB-022–QB-027). Additive companion to the legacy
 * {@code intent} string, which keeps its v1 values.
 */
public record IntentAnalysisResponse(
    String taxonomyVersion,
    String status,
    List<String> issues,
    List<IntentView> intents,
    List<String> retrievalModes,
    List<SubQueryView> subqueries) {

  public IntentAnalysisResponse {
    issues = issues == null ? List.of() : List.copyOf(issues);
    intents = intents == null ? List.of() : List.copyOf(intents);
    retrievalModes = retrievalModes == null ? List.of() : List.copyOf(retrievalModes);
    subqueries = subqueries == null ? List.of() : List.copyOf(subqueries);
  }

  public record IntentView(
      String id, String type, Integer start, Integer end, String confidenceKind) {}

  public record SubQueryView(
      String id, String objective, List<String> intentIds, List<String> dependsOn) {
    public SubQueryView {
      intentIds = List.copyOf(intentIds);
      dependsOn = List.copyOf(dependsOn);
    }
  }

  public static IntentAnalysisResponse from(IntentUnderstanding understanding) {
    if (understanding == null) {
      return null;
    }
    return new IntentAnalysisResponse(
        understanding.taxonomyVersion(),
        understanding.assessment().status().name(),
        understanding.assessment().issues().stream().map(Enum::name).toList(),
        understanding.intents().stream().map(IntentAnalysisResponse::intent).toList(),
        understanding.retrievalHints().modes().stream().map(Enum::name).sorted().toList(),
        understanding.subqueries().stream().map(IntentAnalysisResponse::subquery).toList());
  }

  private static IntentView intent(Intent intent) {
    return new IntentView(
        intent.id(),
        intent.type(),
        intent.span().map(s -> s.start()).orElse(null),
        intent.span().map(s -> s.end()).orElse(null),
        intent.confidence().kind().name());
  }

  private static SubQueryView subquery(SubQuery subquery) {
    return new SubQueryView(
        subquery.id(), subquery.objective(), subquery.intentIds(), subquery.dependsOn());
  }
}
