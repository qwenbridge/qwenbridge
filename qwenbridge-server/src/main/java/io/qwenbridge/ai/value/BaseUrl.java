package io.qwenbridge.ai.value;

import java.net.URI;
import java.util.Objects;

public record BaseUrl(URI value) {

  public BaseUrl {
    Objects.requireNonNull(value, "base url must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
