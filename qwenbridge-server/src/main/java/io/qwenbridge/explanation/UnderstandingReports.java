package io.qwenbridge.explanation;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.core.confidence.ComponentConfidence;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.confidence.ConfidenceComponent;
import io.qwenbridge.core.confidence.ConfidenceReport;
import io.qwenbridge.core.evidence.EvidenceRecord;
import io.qwenbridge.core.evidence.EvidenceSet;
import io.qwenbridge.core.explain.Explanation;
import io.qwenbridge.core.explain.ExplanationSet;
import io.qwenbridge.core.explain.QueryIrExplainer;
import io.qwenbridge.core.intent.IntentUnderstanding;
import io.qwenbridge.core.threat.ThreatAssessment;
import io.qwenbridge.core.threat.ThreatSignal;
import io.qwenbridge.core.translation.TranslationResult;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConstraintValidationResult;
import io.qwenbridge.pipeline.result.ExecutionPlanResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.QueryIrResult;
import io.qwenbridge.pipeline.result.TranslationStepResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the per-component confidence report and the explanations of one v1 request from what the
 * pipeline recorded (QB-078–QB-082). Nothing here calls a model or invents a value: a component
 * that did not run is absent, one that ran without a usable signal is UNKNOWN, and a decision with
 * nothing recorded to explain it is UNKNOWN rather than described.
 */
public final class UnderstandingReports {

  private UnderstandingReports() {}

  public static ConfidenceReport confidence(ExecutionContext context) {
    List<ComponentConfidence> components = new ArrayList<>();
    LanguageResult language = context.get(LanguageResult.class);
    if (language != null && language.analysis() != null) {
      LanguageAnalysis analysis = language.analysis();
      components.add(
          new ComponentConfidence(
              ConfidenceComponent.LANGUAGE,
              analysis.detection().confidence(),
              analysis.detection().detector()));
    }
    IntentUnderstanding intent = context.get(IntentUnderstanding.class);
    if (intent != null && !intent.intents().isEmpty()) {
      components.add(
          new ComponentConfidence(
              ConfidenceComponent.INTENT,
              intent.intents().getFirst().confidence(),
              "intent-taxonomy-" + intent.taxonomyVersion()));
    }
    ThreatAssessment threat = context.get(ThreatAssessment.class);
    if (threat != null) {
      components.add(
          new ComponentConfidence(
              ConfidenceComponent.THREAT,
              threat.signals().isEmpty() ? Confidence.deterministic() : Confidence.heuristic(),
              "threat-rules"));
    }
    TranslationStepResult translation = context.get(TranslationStepResult.class);
    if (translation != null
        && translation.outcome().result() instanceof TranslationResult.Translated t) {
      components.add(
          new ComponentConfidence(ConfidenceComponent.TRANSLATION, t.confidence(), t.providerId()));
    }
    SearchAnalysis model = context.get(SearchAnalysis.class);
    if (model != null) {
      boolean usable = model.semanticValidated();
      components.add(model(ConfidenceComponent.MODEL_INTENT, usable, model.intentConfidence()));
      components.add(model(ConfidenceComponent.MODEL_SEMANTIC, usable, model.semanticScore()));
      components.add(model(ConfidenceComponent.MODEL_DECISION, usable, model.decisionConfidence()));
    }
    return ConfidenceReport.of(components, false);
  }

  private static ComponentConfidence model(
      ConfidenceComponent component, boolean usable, double score) {
    Confidence confidence =
        usable && Double.isFinite(score) && score >= 0 && score <= 1
            ? Confidence.modelScore(score)
            : Confidence.unknown();
    return new ComponentConfidence(component, confidence, "search-analysis-model");
  }

  public static ExplanationSet explanations(ExecutionContext context, Instant observedAt) {
    List<Explanation> explanations = new ArrayList<>();
    List<EvidenceRecord> evidence = new ArrayList<>();

    QueryIrResult ir = context.get(QueryIrResult.class);
    String original = context.request().originalQuery();
    if (ir != null && ir.ir().isPresent()) {
      ConstraintValidationResult validation = context.get(ConstraintValidationResult.class);
      ExplanationSet queryIr =
          QueryIrExplainer.explain(
              ir.ir().get(),
              validation == null ? Optional.empty() : validation.report(),
              observedAt);
      explanations.addAll(queryIr.explanations());
      evidence.addAll(queryIr.evidence().records());
    } else {
      explanations.add(Explanation.unknown("queryIR"));
    }

    ThreatAssessment threat = context.get(ThreatAssessment.class);
    if (threat != null) {
      String detectors =
          threat.signals().stream()
              .map(ThreatSignal::detectorId)
              .distinct()
              .sorted()
              .collect(Collectors.joining(","));
      String ev = "ev-threat";
      evidence.add(
          new EvidenceRecord(
              EvidenceRecord.VERSION,
              ev,
              EvidenceRecord.Kind.POLICY_DECISION,
              "threat-rules",
              detectors.isEmpty() ? "none-matched" : detectors,
              Optional.empty(),
              Optional.empty(),
              observedAt,
              Optional.empty(),
              Map.of("modelStatus", threat.modelStatus().name())));
      String categories =
          threat.signals().stream()
              .map(s -> s.category().name())
              .distinct()
              .sorted()
              .collect(Collectors.joining(", "));
      explanations.add(
          Explanation.supported(
              "threat",
              threat.signals().isEmpty()
                  ? "No threat rule matched; action " + threat.action() + "."
                  : "Threat action "
                      + threat.action()
                      + " from "
                      + threat.signals().size()
                      + " signal(s): "
                      + categories
                      + ".",
              List.of(ev),
              threat.signals().isEmpty() ? Confidence.deterministic() : Confidence.heuristic()));
    }

    ExecutionPlanResult plan = context.get(ExecutionPlanResult.class);
    if (plan != null && plan.available()) {
      String ev = "ev-plan";
      evidence.add(
          EvidenceRecord.of(
              ev,
              EvidenceRecord.Kind.MODEL_OUTPUT,
              "model",
              "decision",
              plan.plan().reason() == null ? "" : plan.plan().reason(),
              observedAt));
      explanations.add(
          Explanation.supported(
              "plan",
              "Plan "
                  + plan.plan().mode()
                  + " on "
                  + plan.plan().backend()
                  + " with "
                  + plan.plan().steps().size()
                  + " step(s), chosen by the model decision.",
              List.of(ev),
              Confidence.modelDecision()));
    } else {
      explanations.add(Explanation.unknown("plan"));
    }
    return ExplanationSet.checked(explanations, new EvidenceSet(evidence), original);
  }
}
