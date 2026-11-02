package io.qwenbridge.core.connector;

import java.util.Map;
import java.util.Set;

/**
 * Connector lifecycle states (QB-157) and their legal transitions.
 *
 * <pre>
 * DISABLED                       (not configured; needs no settings)
 * CREATED → STARTING → READY ⇄ DEGRADED
 *                    ↘ FAILED
 * READY | DEGRADED | FAILED → STOPPING → STOPPED
 * FAILED | STOPPED → STARTING     (restart)
 * </pre>
 */
public enum ConnectorState {
  DISABLED,
  CREATED,
  STARTING,
  READY,
  DEGRADED,
  FAILED,
  STOPPING,
  STOPPED;

  private static final Map<ConnectorState, Set<ConnectorState>> NEXT =
      Map.of(
          DISABLED, Set.of(),
          CREATED, Set.of(STARTING),
          STARTING, Set.of(READY, DEGRADED, FAILED),
          READY, Set.of(DEGRADED, STOPPING, FAILED),
          DEGRADED, Set.of(READY, STOPPING, FAILED),
          FAILED, Set.of(STARTING, STOPPING),
          STOPPING, Set.of(STOPPED),
          STOPPED, Set.of(STARTING));

  public boolean canMoveTo(ConnectorState next) {
    return NEXT.get(this).contains(next);
  }

  /** Whether operations are accepted: READY, or DEGRADED (working with reduced service). */
  public boolean acceptsOperations() {
    return this == READY || this == DEGRADED;
  }
}
