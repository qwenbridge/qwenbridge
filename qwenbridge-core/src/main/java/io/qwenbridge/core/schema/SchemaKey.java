package io.qwenbridge.core.schema;

import java.util.Objects;

/** Identity of one immutable schema version. */
public record SchemaKey(String id, SchemaVersion version) {

  public SchemaKey {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(version, "version must not be null");
  }

  @Override
  public String toString() {
    return id + "@" + version;
  }
}
