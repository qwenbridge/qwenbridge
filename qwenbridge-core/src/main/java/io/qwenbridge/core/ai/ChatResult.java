package io.qwenbridge.core.ai;

import java.util.Objects;

/** Typed outcome of a chat call (QB-243). Failures are values, never exceptions. */
public sealed interface ChatResult {

  record Completed(String text, ModelDescriptor model) implements ChatResult {
    public Completed {
      Objects.requireNonNull(text, "text must not be null");
      Objects.requireNonNull(model, "model must not be null");
    }
  }

  record Failed(Failure failure, String providerId) implements ChatResult {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      Objects.requireNonNull(providerId, "providerId must not be null");
    }
  }

  enum Failure {
    NO_PROVIDER,
    UNAVAILABLE,
    TIMEOUT,
    CANCELLED,
    RATE_LIMITED,
    /** The provider lacks a capability the call needs (checked before calling). */
    UNSUPPORTED,
    INPUT_TOO_LARGE,
    INVALID_OUTPUT;

    /** Failures of the provider (not of the request or the caller) that a fallback may cover. */
    public boolean providerFault() {
      return this == UNAVAILABLE || this == TIMEOUT || this == RATE_LIMITED || this == UNSUPPORTED;
    }
  }
}
