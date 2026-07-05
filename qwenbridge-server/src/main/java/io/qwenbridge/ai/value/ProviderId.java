package io.qwenbridge.ai.value;

import java.util.Objects;

public record ProviderId(String value) {

  public ProviderId {
    value = Objects.requireNonNull(value, "provider id must not be null").trim().toLowerCase();
  }

  @Override
  public String toString() {
    return value;
  }
}
