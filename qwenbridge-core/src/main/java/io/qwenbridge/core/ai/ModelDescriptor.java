package io.qwenbridge.core.ai;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Public identity of a model behind a provider (QB-247): provider, model name, model version and
 * the configuration that affects its output (temperature, context size). Credentials never belong
 * here; a configuration key that names one is refused, so metadata can be shown and logged.
 */
public record ModelDescriptor(
    String provider, String model, String version, Map<String, String> configuration) {

  private static final Pattern SECRET_KEY =
      Pattern.compile("(?i).*(secret|token|password|passwd|api[-_.]?key|credential|auth).*");

  public ModelDescriptor {
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(model, "model must not be null");
    Objects.requireNonNull(version, "version must not be null");
    if (provider.isBlank() || model.isBlank() || version.isBlank())
      throw new IllegalArgumentException("provider, model and version must be named");
    configuration = configuration == null ? Map.of() : Map.copyOf(new TreeMap<>(configuration));
    for (String key : configuration.keySet())
      if (SECRET_KEY.matcher(key).matches())
        throw new IllegalArgumentException(
            "model metadata must not carry credentials (" + key + ")");
  }

  @Override
  public String toString() {
    return provider + ":" + model + "@" + version;
  }
}
