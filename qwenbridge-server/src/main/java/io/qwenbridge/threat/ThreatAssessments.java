package io.qwenbridge.threat;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.threat.ThreatAction;
import io.qwenbridge.core.threat.ThreatAssessment;
import io.qwenbridge.core.threat.ThreatAssessment.ModelStatus;
import io.qwenbridge.core.threat.ThreatCategory;
import io.qwenbridge.core.threat.ThreatSeverity;
import io.qwenbridge.core.threat.ThreatSignal;
import io.qwenbridge.core.threat.TrustZone;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatDecision;
import io.qwenbridge.threat.model.ThreatFinding;
import java.util.ArrayList;
import java.util.List;

/**
 * Expresses the user-input threat decision as a Core {@link ThreatAssessment} (QB-064–QB-069).
 *
 * <ul>
 *   <li>Rule findings become RULE signals. Their YAML "confidence" numbers are hand-set, so they
 *       are reported as {@code HEURISTIC} without a number (QB-068).
 *   <li>The model's safety decision becomes a MODEL signal with {@code MODEL_DECISION} confidence.
 *       It can only make the action stricter (QB-066).
 *   <li>When the model did not answer, rules decided alone and the status is {@code UNAVAILABLE}
 *       (QB-069). The pipeline still refuses to execute filters the model was meant to check; that
 *       is an execution control, separate from the threat decision.
 * </ul>
 */
public final class ThreatAssessments {

  private ThreatAssessments() {}

  /** Rules have decided before any model ran (the request may already be stopped). */
  public static ThreatAssessment rulesOnly(ThreatAnalysis rules) {
    ThreatAssessment base = of(rules, null);
    return new ThreatAssessment(
        base.zone(),
        base.action(),
        base.signals(),
        ModelStatus.NOT_EVALUATED,
        base.failedDetectors());
  }

  public static ThreatAssessment of(ThreatAnalysis rules, SearchAnalysis model) {
    List<ThreatSignal> signals = new ArrayList<>();
    for (ThreatFinding finding : rules.findings()) {
      signals.add(
          new ThreatSignal(
              category(finding.type().name()),
              ThreatSeverity.valueOf(finding.severity().name()),
              Confidence.heuristic(),
              ThreatSignal.Source.RULE,
              finding.detector(),
              finding.type().name().toLowerCase(java.util.Locale.ROOT),
              finding.evidence()));
    }
    ThreatAction action = action(rules.decision());
    ModelStatus status;
    if (model == null) {
      status = ModelStatus.NOT_CONFIGURED;
    } else if (!model.semanticValidated() && !"ALLOW".equals(model.modelThreatDecision())) {
      status = ModelStatus.UNAVAILABLE;
    } else {
      status = ModelStatus.EVALUATED;
      String decision = model.modelThreatDecision();
      if ("BLOCK".equals(decision) || "REVIEW".equals(decision)) {
        ThreatSeverity severity =
            "BLOCK".equals(decision) ? ThreatSeverity.HIGH : ThreatSeverity.MEDIUM;
        signals.add(
            new ThreatSignal(
                ThreatCategory.OTHER,
                severity,
                Confidence.modelDecision(),
                ThreatSignal.Source.MODEL,
                "model-safety",
                decision.toLowerCase(java.util.Locale.ROOT),
                "model safety decision"));
        action =
            action.strictest("BLOCK".equals(decision) ? ThreatAction.BLOCK : ThreatAction.REVIEW);
      }
    }
    return new ThreatAssessment(TrustZone.USER_INPUT, action, signals, status, List.of());
  }

  private static ThreatAction action(ThreatDecision decision) {
    return switch (decision) {
      case ALLOW -> ThreatAction.ALLOW;
      case REVIEW -> ThreatAction.REVIEW;
      case BLOCK -> ThreatAction.BLOCK;
    };
  }

  private static ThreatCategory category(String type) {
    try {
      return ThreatCategory.valueOf(type);
    } catch (IllegalArgumentException e) {
      return ThreatCategory.OTHER;
    }
  }
}
