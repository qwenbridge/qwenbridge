package io.qwenbridge.core.connector;

import java.util.Set;

/**
 * What a connector's configuration may contain (QB-155): required and optional plain settings, and
 * required and optional secrets. A secret key may only be given as a {@link SecretRef}.
 */
public record ConfigSchema(
    Set<String> requiredSettings,
    Set<String> optionalSettings,
    Set<String> requiredSecrets,
    Set<String> optionalSecrets) {
  public ConfigSchema {
    requiredSettings = Set.copyOf(requiredSettings);
    optionalSettings = Set.copyOf(optionalSettings);
    requiredSecrets = Set.copyOf(requiredSecrets);
    optionalSecrets = Set.copyOf(optionalSecrets);
  }

  public static final ConfigSchema NONE = new ConfigSchema(Set.of(), Set.of(), Set.of(), Set.of());

  boolean secret(String key) {
    return requiredSecrets.contains(key) || optionalSecrets.contains(key);
  }

  boolean setting(String key) {
    return requiredSettings.contains(key) || optionalSettings.contains(key);
  }
}
