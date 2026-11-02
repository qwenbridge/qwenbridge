package io.qwenbridge.core.policy;

import java.util.Objects;

/**
 * An operation and what it does (QB-074). Classification is by effect, not by HTTP verb or name: an
 * operation that changes stored state, configuration or installed code is {@link Effect#MUTATE}
 * even if it is called through a read-looking endpoint.
 */
public record OperationSpec(String name, Effect effect, Sensitivity sensitivity) {

  public enum Effect {
    /** Reads data or configuration; changes nothing. */
    READ,
    /** Changes data, configuration, installed packs, licenses or plugins. */
    MUTATE
  }

  public enum Sensitivity {
    /** Ordinary use; may be open to anonymous principals that hold the scope. */
    NORMAL,
    /** Needs an authenticated principal with an explicit scope. */
    SENSITIVE,
    /** As SENSITIVE, plus an approval from a second person (QB-075). */
    HIGH_RISK
  }

  public OperationSpec {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(effect, "effect must not be null");
    Objects.requireNonNull(sensitivity, "sensitivity must not be null");
    if (sensitivity == Sensitivity.HIGH_RISK && effect == Effect.READ) {
      throw new IllegalArgumentException("high-risk operations must be mutating");
    }
  }

  public boolean mutating() {
    return effect == Effect.MUTATE;
  }
}
