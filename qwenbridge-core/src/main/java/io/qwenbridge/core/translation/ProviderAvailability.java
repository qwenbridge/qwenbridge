package io.qwenbridge.core.translation;

import java.util.Objects;

/**
 * Whether a {@link TranslationProvider} can serve requests now (QB-286).
 *
 * @param detail a short explanation for operators, never a secret
 */
public record ProviderAvailability(State state, String detail) {

  public enum State {
    /** Initialized and able to translate. */
    READY,
    /** Still loading; not called yet. */
    INITIALIZING,
    /** The model it depends on is not available. */
    MODEL_UNAVAILABLE,
    /** Required local resources (word lists, model files) are missing or unreadable. */
    MISSING_RESOURCES,
    /** Initialization failed. */
    FAILED
  }

  public ProviderAvailability {
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(detail, "detail must not be null");
  }

  public static ProviderAvailability ready() {
    return new ProviderAvailability(State.READY, "ready");
  }

  public static ProviderAvailability of(State state, String detail) {
    return new ProviderAvailability(state, detail);
  }

  public boolean isReady() {
    return state == State.READY;
  }
}
