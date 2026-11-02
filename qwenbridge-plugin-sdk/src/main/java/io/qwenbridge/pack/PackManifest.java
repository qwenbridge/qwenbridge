package io.qwenbridge.pack;

import java.util.*;

public record PackManifest(
    String id,
    String version,
    int apiVersion,
    Kind kind,
    String publisher,
    List<Dependency> dependencies,
    Set<String> capabilities,
    Map<String, ConfigField> configuration,
    Runtime runtime) {

  /**
   * The QwenBridge runtime versions a pack supports (QB-257): {@code minimum} inclusive, {@code
   * maximumExclusive} exclusive (optional). A pack without it runs on any runtime of its API
   * version.
   */
  public record Runtime(String minimum, String maximumExclusive) {
    public Runtime {
      versionParts(minimum);
      if (maximumExclusive != null) {
        versionParts(maximumExclusive);
        if (compare(minimum, maximumExclusive) >= 0)
          throw new IllegalArgumentException("Empty runtime range");
      }
    }

    public boolean accepts(String version) {
      return compare(version, minimum) >= 0
          && (maximumExclusive == null || compare(version, maximumExclusive) < 0);
    }
  }

  /** A manifest without a runtime range. */
  public PackManifest(
      String id,
      String version,
      int apiVersion,
      Kind kind,
      String publisher,
      List<Dependency> dependencies,
      Set<String> capabilities,
      Map<String, ConfigField> configuration) {
    this(id, version, apiVersion, kind, publisher, dependencies, capabilities, configuration, null);
  }

  public enum Kind {
    DOMAIN,
    CONNECTOR,
    POLICY
  }

  public enum ConfigType {
    STRING,
    INTEGER,
    BOOLEAN,
    URI,
    SECRET_REF
  }

  public record ConfigField(ConfigType type, boolean required, String description) {
    public ConfigField {
      Objects.requireNonNull(type);
      Objects.requireNonNull(description);
    }
  }

  public record Dependency(String id, String minimum, String maximumExclusive) {
    public Dependency {
      validId(id);
      versionParts(minimum);
      if (maximumExclusive != null) versionParts(maximumExclusive);
    }

    public boolean accepts(String version) {
      return compare(version, minimum) >= 0
          && (maximumExclusive == null || compare(version, maximumExclusive) < 0);
    }
  }

  public PackManifest {
    validId(id);
    validId(publisher);
    versionParts(version);
    Objects.requireNonNull(kind);
    if (apiVersion != 1) throw new IllegalArgumentException("Unsupported pack API version");
    dependencies = List.copyOf(dependencies);
    capabilities = Set.copyOf(capabilities);
    configuration = Map.copyOf(configuration);
    if (dependencies.stream().map(Dependency::id).distinct().count() != dependencies.size()
        || dependencies.stream().anyMatch(d -> d.id().equals(id)))
      throw new IllegalArgumentException("Invalid pack dependencies");
  }

  public static void validId(String id) {
    if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}"))
      throw new IllegalArgumentException("Invalid pack identifier");
  }

  public static int[] versionParts(String version) {
    if (version == null
        || !version.matches(
            "(?:0|[1-9][0-9]{0,5})\\.(?:0|[1-9][0-9]{0,5})\\.(?:0|[1-9][0-9]{0,5})"))
      throw new IllegalArgumentException("Invalid semantic version");
    return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
  }

  public static int compare(String first, String second) {
    int[] a = versionParts(first), b = versionParts(second);
    for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
    return 0;
  }
}
