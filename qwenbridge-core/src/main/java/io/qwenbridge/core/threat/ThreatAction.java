package io.qwenbridge.core.threat;

/**
 * What to do with the inspected text (QB-064). This is a content-safety decision only: it is never
 * an authorization. {@link #ALLOW} means "no threat requires action", not "permitted"; whether the
 * caller may perform the operation is decided separately by the policy engine.
 *
 * <p>Actions are ordered by strictness; combining assessments always keeps the strictest.
 */
public enum ThreatAction {
  /** Nothing found that requires action. */
  ALLOW,
  /** Continue only with human or policy review; nothing is executed automatically. */
  REVIEW,
  /** Keep the content out of model context and answers; the request itself may continue. */
  QUARANTINE,
  /** Stop the request. */
  BLOCK;

  public ThreatAction strictest(ThreatAction other) {
    return other != null && other.ordinal() > ordinal() ? other : this;
  }
}
