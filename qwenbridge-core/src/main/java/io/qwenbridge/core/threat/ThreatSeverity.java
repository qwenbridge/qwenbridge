package io.qwenbridge.core.threat;

/** Ordered severity of a threat signal (QB-068). It is a category, not a probability. */
public enum ThreatSeverity {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
