package io.qwenbridge.core.threat;

import io.qwenbridge.core.threat.ThreatAssessment.ModelStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs threat detection for one text (QB-064–QB-069).
 *
 * <ol>
 *   <li>Rule detectors are mandatory: an engine cannot be built without one, and a rule detector
 *       that fails makes the result fail closed.
 *   <li>The model classifier is optional. Its signals are mapped by the same policy and can only
 *       make the action stricter; it can never lower a rule decision or allow anything. Model
 *       signals must say they come from a model.
 *   <li>Without a model, or when it fails, rules decide alone and the assessment says so ({@link
 *       ModelStatus#NOT_CONFIGURED} or {@link ModelStatus#UNAVAILABLE}).
 * </ol>
 */
public final class ThreatEngine {

  private final List<ThreatDetector> rules;
  private final Optional<ThreatDetector> model;
  private final ThreatPolicy policy;

  public ThreatEngine(
      List<ThreatDetector> rules, Optional<ThreatDetector> model, ThreatPolicy policy) {
    this.rules = List.copyOf(rules);
    this.model = model == null ? Optional.empty() : model;
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    if (this.rules.isEmpty()) {
      throw new IllegalArgumentException("at least one rule detector is mandatory");
    }
    if (this.rules.stream().anyMatch(d -> d.source() != ThreatSignal.Source.RULE)) {
      throw new IllegalArgumentException("rule detectors must be rule-based");
    }
  }

  public ThreatAssessment assess(ThreatInput input) {
    Objects.requireNonNull(input, "input must not be null");
    List<ThreatSignal> signals = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    ThreatAction action = ThreatAction.ALLOW;
    for (ThreatDetector detector : rules) {
      try {
        for (ThreatSignal signal : detector.detect(input)) {
          signals.add(signal);
          action = action.strictest(policy.actionFor(input.zone(), signal.severity()));
        }
      } catch (RuntimeException e) {
        failed.add(detector.id());
        action = action.strictest(failClosed(input.zone()));
      }
    }
    ModelStatus status = ModelStatus.NOT_CONFIGURED;
    if (model.isPresent()) {
      try {
        List<ThreatSignal> modelSignals = model.get().detect(input);
        for (ThreatSignal signal : modelSignals) {
          if (signal.source() != ThreatSignal.Source.MODEL) {
            throw new IllegalStateException("model signals must be marked as model signals");
          }
          signals.add(signal);
          action = action.strictest(policy.actionFor(input.zone(), signal.severity()));
        }
        status = ModelStatus.EVALUATED;
      } catch (RuntimeException e) {
        status = ModelStatus.UNAVAILABLE;
      }
    }
    return new ThreatAssessment(input.zone(), action, signals, status, failed);
  }

  private static ThreatAction failClosed(TrustZone zone) {
    return zone == TrustZone.USER_INPUT ? ThreatAction.BLOCK : ThreatAction.QUARANTINE;
  }
}
