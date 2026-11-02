package io.qwenbridge.platform;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class PackSecrets implements io.qwenbridge.pack.QwenBridgePack.SecretResolver {
  private final Set<String> allowed;

  public PackSecrets(
      @ConfigProperty(name = "qwenbridge.packs.allowed-secret-env") Optional<String> names) {
    allowed = Set.copyOf(Arrays.asList(names.orElse("").split(",")));
  }

  public char[] resolve(String reference) {
    if (reference == null
        || !reference.matches("env:[A-Z][A-Z0-9_]*")
        || !allowed.contains(reference.substring(4)))
      throw new IllegalArgumentException("Secret reference is not permitted by the operator");
    String value = System.getenv(reference.substring(4));
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Referenced secret is unavailable");
    return value.toCharArray();
  }
}
