package io.qwenbridge.catalog.mapping;

/** Why a source record cannot be mapped, with the source path at fault. */
public record MappingIssue(String path, String message) {
  public MappingIssue {
    if (path == null || message == null) throw new IllegalArgumentException("Invalid issue");
  }
}
