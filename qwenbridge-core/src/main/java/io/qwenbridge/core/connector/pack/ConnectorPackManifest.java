package io.qwenbridge.core.connector.pack;

import io.qwenbridge.core.connector.ConnectorCapabilities;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The package contract for one or more {@link io.qwenbridge.core.connector.Connector}s (QB-237). It
 * carries identity and compatibility only, never configuration values or credentials: settings and
 * secrets are declared by each connector's {@code configSchema()} and given at enable time as a
 * {@link io.qwenbridge.core.connector.ConnectorConfig} with secret references.
 *
 * @param spiMinimum lowest Connector SPI version the pack needs (inclusive)
 * @param spiMaximumExclusive first Connector SPI version the pack does not support
 * @param connectors the connectors the pack can create, each with its system and the most it may
 *     ever do; a started connector that reports more is failed
 */
public record ConnectorPackManifest(
    String id,
    String version,
    String publisher,
    String spiMinimum,
    String spiMaximumExclusive,
    List<Declaration> connectors) {

  /** The Connector SPI this runtime implements. */
  public static final String SPI_VERSION = "1.0.0";

  private static final Pattern ID = Pattern.compile("[a-z][a-z0-9.-]{0,63}");
  private static final Pattern SEMVER =
      Pattern.compile("(?:0|[1-9][0-9]{0,5})\\.(?:0|[1-9][0-9]{0,5})\\.(?:0|[1-9][0-9]{0,5})");

  /** One connector of the pack. */
  public record Declaration(
      String id, String system, String description, ConnectorCapabilities capabilities) {
    public Declaration {
      requireId(id, "connector id");
      requireId(system, "system");
      Objects.requireNonNull(description, "description must not be null");
      Objects.requireNonNull(capabilities, "capabilities must not be null");
      if (!capabilities.allows(ConnectorCapabilities.Access.READ))
        throw new IllegalArgumentException("A connector must at least read");
    }
  }

  public ConnectorPackManifest {
    requireId(id, "pack id");
    requireId(publisher, "publisher");
    requireVersion(version);
    requireVersion(spiMinimum);
    requireVersion(spiMaximumExclusive);
    if (compare(spiMinimum, spiMaximumExclusive) >= 0)
      throw new IllegalArgumentException("Empty Connector SPI range");
    connectors = List.copyOf(connectors);
    if (connectors.isEmpty()) throw new IllegalArgumentException("A pack declares connectors");
    Set<String> ids = new HashSet<>();
    for (var c : connectors)
      if (!ids.add(c.id())) throw new IllegalArgumentException("Duplicate connector " + c.id());
  }

  public Set<String> systems() {
    return connectors.stream()
        .map(Declaration::system)
        .collect(java.util.stream.Collectors.toSet());
  }

  /** Whether this pack runs on the given Connector SPI version. */
  public boolean supportsSpi(String spi) {
    return compare(spi, spiMinimum) >= 0 && compare(spi, spiMaximumExclusive) < 0;
  }

  static void requireId(String value, String what) {
    if (value == null || !ID.matcher(value).matches())
      throw new IllegalArgumentException("Invalid " + what);
  }

  static void requireVersion(String value) {
    if (value == null || !SEMVER.matcher(value).matches())
      throw new IllegalArgumentException("Invalid semantic version");
  }

  static int compare(String first, String second) {
    int[] a = Arrays.stream(first.split("\\.")).mapToInt(Integer::parseInt).toArray();
    int[] b = Arrays.stream(second.split("\\.")).mapToInt(Integer::parseInt).toArray();
    for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
    return 0;
  }
}
