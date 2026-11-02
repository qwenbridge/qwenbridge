package io.qwenbridge.plugin;

import io.qwenbridge.pack.PackManifest;

/** A semantic version range: {@code minimum} inclusive, {@code maximumExclusive} optional. */
public record VersionRange(String minimum, String maximumExclusive) {
  public VersionRange {
    PackManifest.versionParts(minimum);
    if (maximumExclusive != null) {
      PackManifest.versionParts(maximumExclusive);
      if (PackManifest.compare(minimum, maximumExclusive) >= 0)
        throw new IllegalArgumentException(
            "Empty version range " + minimum + ".." + maximumExclusive);
    }
  }

  public boolean includes(String version) {
    return PackManifest.compare(version, minimum) >= 0
        && (maximumExclusive == null || PackManifest.compare(version, maximumExclusive) < 0);
  }

  @Override
  public String toString() {
    return ">=" + minimum + (maximumExclusive == null ? "" : " <" + maximumExclusive);
  }
}
