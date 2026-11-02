package io.qwenbridge.confidence;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.intent.IntentType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConfidenceService {
  public double calculate(String query, List<String> rewrites) {
    // Rewrite count alone provides no evidence of decision confidence.
    return 0.0;
  }

  /** Conservatively combines model signals without treating them as calibrated probabilities. */
  public ConfidenceAssessment assess(SearchAnalysis analysis) {
    if (analysis == null
        || analysis.intent() == IntentType.UNKNOWN
        || !analysis.semanticValidated()) {
      return new ConfidenceAssessment(0.0, "unavailable", false, Map.of());
    }
    double intent = finite(analysis.intentConfidence());
    double semantic = finite(analysis.semanticScore());
    double decision = finite(analysis.decisionConfidence());
    return new ConfidenceAssessment(
        Math.min(intent, Math.min(semantic, decision)),
        "model-signal-min-v1",
        false,
        Map.of("intent", intent, "semantic", semantic, "decision", decision));
  }

  private static double finite(double value) {
    return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
  }
}
