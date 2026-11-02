package io.qwenbridge.core.threat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps a signal's severity in a trust zone to an action (QB-068). Thresholds are categorical and
 * configurable; no hand-set "probability" decides anything.
 *
 * <p>Mandatory controls cannot be configured away: in {@link TrustZone#USER_INPUT} a {@code
 * CRITICAL} or {@code HIGH} signal always blocks, and in every other zone a {@code HIGH} or {@code
 * CRITICAL} signal keeps the content out of model context at least.
 */
public final class ThreatPolicy {

  private final Map<TrustZone, Map<ThreatSeverity, ThreatAction>> actions;

  public ThreatPolicy(Map<TrustZone, Map<ThreatSeverity, ThreatAction>> actions) {
    Objects.requireNonNull(actions, "actions must not be null");
    EnumMap<TrustZone, Map<ThreatSeverity, ThreatAction>> copy = new EnumMap<>(TrustZone.class);
    for (TrustZone zone : TrustZone.values()) {
      Map<ThreatSeverity, ThreatAction> byZone = actions.get(zone);
      if (byZone == null) {
        throw new IllegalArgumentException("policy must define zone " + zone);
      }
      EnumMap<ThreatSeverity, ThreatAction> severities = new EnumMap<>(ThreatSeverity.class);
      for (ThreatSeverity severity : ThreatSeverity.values()) {
        ThreatAction action = byZone.get(severity);
        if (action == null) {
          throw new IllegalArgumentException("policy must define " + zone + "/" + severity);
        }
        severities.put(severity, action);
      }
      copy.put(zone, Map.copyOf(severities));
    }
    this.actions = Map.copyOf(copy);
    checkFloor(TrustZone.USER_INPUT, ThreatSeverity.CRITICAL, ThreatAction.BLOCK);
    checkFloor(TrustZone.USER_INPUT, ThreatSeverity.HIGH, ThreatAction.BLOCK);
    for (TrustZone zone : TrustZone.values()) {
      if (zone != TrustZone.USER_INPUT) {
        checkFloor(zone, ThreatSeverity.CRITICAL, ThreatAction.QUARANTINE);
        checkFloor(zone, ThreatSeverity.HIGH, ThreatAction.QUARANTINE);
      }
    }
  }

  /**
   * The default policy, equivalent to the released rule thresholds: in user input HIGH and CRITICAL
   * block, MEDIUM needs review, LOW is allowed; elsewhere MEDIUM and above are quarantined.
   */
  public static ThreatPolicy defaults() {
    Map<TrustZone, Map<ThreatSeverity, ThreatAction>> actions = new EnumMap<>(TrustZone.class);
    actions.put(
        TrustZone.USER_INPUT,
        Map.of(
            ThreatSeverity.LOW, ThreatAction.ALLOW,
            ThreatSeverity.MEDIUM, ThreatAction.REVIEW,
            ThreatSeverity.HIGH, ThreatAction.BLOCK,
            ThreatSeverity.CRITICAL, ThreatAction.BLOCK));
    Map<ThreatSeverity, ThreatAction> content =
        Map.of(
            ThreatSeverity.LOW, ThreatAction.ALLOW,
            ThreatSeverity.MEDIUM, ThreatAction.QUARANTINE,
            ThreatSeverity.HIGH, ThreatAction.QUARANTINE,
            ThreatSeverity.CRITICAL, ThreatAction.QUARANTINE);
    actions.put(TrustZone.RETRIEVED_CONTENT, content);
    actions.put(TrustZone.TOOL_OUTPUT, content);
    actions.put(TrustZone.MODEL_OUTPUT, content);
    return new ThreatPolicy(actions);
  }

  public ThreatAction actionFor(TrustZone zone, ThreatSeverity severity) {
    return actions.get(zone).get(severity);
  }

  private void checkFloor(TrustZone zone, ThreatSeverity severity, ThreatAction minimum) {
    if (actionFor(zone, severity).ordinal() < minimum.ordinal()) {
      throw new IllegalArgumentException(
          "mandatory control: " + zone + "/" + severity + " must be at least " + minimum);
    }
  }
}
