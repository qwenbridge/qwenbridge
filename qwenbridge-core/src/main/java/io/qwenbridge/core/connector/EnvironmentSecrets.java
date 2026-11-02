package io.qwenbridge.core.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves {@code env:} references from the environment and {@code file:} references from files
 * under one allowed directory (for example a container's secrets mount), up to 64 KiB, with a
 * trailing newline removed (QB-156).
 */
public final class EnvironmentSecrets implements SecretResolver {
  static final int MAX_FILE_BYTES = 65_536;

  private final Function<String, String> environment;
  private final Optional<Path> fileRoot;

  public EnvironmentSecrets(Function<String, String> environment, Optional<Path> fileRoot) {
    this.environment = environment;
    this.fileRoot = fileRoot.map(p -> p.toAbsolutePath().normalize());
  }

  public static EnvironmentSecrets system(Optional<Path> fileRoot) {
    return new EnvironmentSecrets(System::getenv, fileRoot);
  }

  @Override
  public Secret resolve(SecretRef reference) {
    if (reference.environment()) {
      String value = environment.apply(reference.target());
      if (value == null || value.isEmpty()) {
        throw new IllegalArgumentException("secret " + reference.reference() + " is not set");
      }
      return new Secret(value.toCharArray());
    }
    Path root =
        fileRoot.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "file secrets are not enabled for " + reference.reference()));
    Path file = Path.of(reference.target()).toAbsolutePath().normalize();
    if (!file.startsWith(root)) {
      throw new IllegalArgumentException(
          "secret " + reference.reference() + " is outside the secrets directory");
    }
    try {
      if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
        throw new IllegalArgumentException("secret " + reference.reference() + " is unavailable");
      }
      String value = Files.readString(file, StandardCharsets.UTF_8);
      if (value.endsWith("\n")) {
        value = value.substring(0, value.length() - (value.endsWith("\r\n") ? 2 : 1));
      }
      if (value.isEmpty()) {
        throw new IllegalArgumentException("secret " + reference.reference() + " is empty");
      }
      return new Secret(value.toCharArray());
    } catch (IOException e) {
      throw new IllegalArgumentException("secret " + reference.reference() + " is unreadable", e);
    }
  }
}
