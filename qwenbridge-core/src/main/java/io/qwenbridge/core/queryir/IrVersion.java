package io.qwenbridge.core.queryir;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QueryIR schema version {@code major.minor} (QB-037).
 *
 * <p>Compatibility rules:
 *
 * <ul>
 *   <li>A minor version only adds optional fields. A reader supporting {@code 1.n} accepts every
 *       {@code 1.m} with {@code m <= n}; absent optional fields take their documented defaults.
 *   <li>A document with a higher minor than the reader supports is rejected: it may contain fields
 *       whose meaning the reader cannot honour, and silently dropping them could change the query's
 *       semantics.
 *   <li>A different major version is a breaking change and is always rejected.
 *   <li>Unknown fields are rejected everywhere except inside {@code extensions}.
 * </ul>
 */
public record IrVersion(int major, int minor) implements Comparable<IrVersion> {

  /** The version this implementation reads and writes. */
  public static final IrVersion CURRENT = new IrVersion(1, 0);

  /** 1.1 adds the optional {@code correction} field (QB-291); documents without it stay 1.0. */
  public static final IrVersion V1_1 = new IrVersion(1, 1);

  /** The newest version this implementation reads. */
  public static final IrVersion READABLE = V1_1;

  private static final Pattern FORMAT =
      Pattern.compile("^(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})$");

  public IrVersion {
    if (major < 0 || minor < 0) {
      throw new IllegalArgumentException("version numbers must not be negative");
    }
  }

  public static Optional<IrVersion> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = FORMAT.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new IrVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
  }

  /** Whether a reader at {@code this} version can safely read a document at {@code document}. */
  public boolean canRead(IrVersion document) {
    return document.major == major && document.minor <= minor;
  }

  @Override
  public int compareTo(IrVersion other) {
    return major != other.major
        ? Integer.compare(major, other.major)
        : Integer.compare(minor, other.minor);
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }
}
