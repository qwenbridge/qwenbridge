package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.core.threat.ThreatAssessment;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.threat.ThreatAssessments;
import io.qwenbridge.threat.ThreatResult;
import io.qwenbridge.threat.model.ThreatAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Rules run first; the model can add a stop, never undo a deterministic stop. */
@ApplicationScoped
public class ModelSafetyStep implements PipelineStep<ThreatResult> {
  public PipelineStage stage() {
    return PipelineStage.THREAT;
  }

  public String name() {
    return "ModelSafetyStep";
  }

  public int order() {
    return 26;
  }

  public boolean publishEvents() {
    return false;
  }

  public Class<ThreatResult> resultType() {
    return ThreatResult.class;
  }

  public ThreatResult execute(ExecutionContext context) {
    ThreatResult deterministic = context.get(ThreatResult.class);
    SearchAnalysis analysis = context.get(SearchAnalysis.class);
    ThreatAnalysis rules = context.get(ThreatAnalysis.class);
    if (rules != null) {
      // The typed view of the combined decision (QB-064–QB-069); it never relaxes the result below.
      context.store(ThreatAssessment.class, ThreatAssessments.of(rules, analysis));
    }
    if (analysis != null
        && !analysis.semanticValidated()
        && !"ALLOW".equals(analysis.modelThreatDecision()))
      return new ThreatResult(
          false, List.of("AI analysis unavailable; unchecked filters cannot be executed"));
    if (analysis != null && List.of("BLOCK", "REVIEW").contains(analysis.modelThreatDecision()))
      return new ThreatResult(
          false, List.of("Model threat decision: " + analysis.modelThreatDecision()));
    return deterministic;
  }
}
