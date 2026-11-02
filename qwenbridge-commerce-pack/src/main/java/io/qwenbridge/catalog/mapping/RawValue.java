package io.qwenbridge.catalog.mapping;

/**
 * A source value together with the source path it was read from, so normalization can always record
 * where a canonical value came from (QB-092).
 *
 * @param path the source field path, for example {@code variants[0].price}; {@code $recordId} when
 *     the value is the record identity itself
 */
public record RawValue(String path, Object value) {
  public RawValue {
    if (path == null || path.isBlank()) throw new IllegalArgumentException("Source path required");
  }

  public static RawValue of(String path, Object value) {
    return value == null ? null : new RawValue(path, value);
  }
}
