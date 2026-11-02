package io.qwenbridge.core.schema;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain schema version {@code major.minor} (QB-046). Within one major version every new minor must
 * be backward compatible: fields may be added and operators or allowed values widened, but nothing
 * may be removed, retyped or narrowed. Any such change requires a new major version.
 */
public record SchemaVersion(int major, int minor) implements Comparable<SchemaVersion> {

  private static final Pattern FORMAT =
      Pattern.compile("^([1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})$");

  public SchemaVersion {
    if (major < 1 || minor < 0) {
      throw new IllegalArgumentException("schema major must be >= 1 and minor >= 0");
    }
  }

  public static Optional<SchemaVersion> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = FORMAT.matcher(text);
    return matcher.matches()
        ? Optional.of(
            new SchemaVersion(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))))
        : Optional.empty();
  }

  @Override
  public int compareTo(SchemaVersion other) {
    return major != other.major
        ? Integer.compare(major, other.major)
        : Integer.compare(minor, other.minor);
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }
}
