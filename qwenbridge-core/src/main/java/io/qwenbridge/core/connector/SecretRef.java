package io.qwenbridge.core.connector;

import java.util.Objects;

/**
 * A reference to a secret, never the secret itself (QB-156): {@code env:NAME} or {@code
 * file:/absolute/path}. Configurations and manifests hold references only; the value is resolved at
 * start by a {@link SecretResolver}.
 */
public record SecretRef(String reference) {
  public SecretRef {
    Objects.requireNonNull(reference, "reference must not be null");
    if (!isReference(reference)) {
      throw new IllegalArgumentException("a secret must be given as env:NAME or file:/path");
    }
  }

  public static boolean isReference(String value) {
    return value != null
        && (value.matches("env:[A-Z][A-Z0-9_]{0,127}")
            || value.matches("file:/[A-Za-z0-9._/-]{1,255}") && !value.contains(".."));
  }

  public boolean environment() {
    return reference.startsWith("env:");
  }

  public String target() {
    return reference.substring(reference.indexOf(':') + 1);
  }
}
