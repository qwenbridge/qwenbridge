package io.qwenbridge.catalog.canonical;

import java.util.List;

/**
 * A normalized category with its path from the taxonomy root (QB-094). An unrecognized category
 * keeps its lower-cased source value as id and has a one-element path.
 */
public record CategoryRef(String id, List<String> path, boolean recognized, Provenance provenance) {
  public CategoryRef {
    if (id == null || id.isBlank() || provenance == null)
      throw new IllegalArgumentException("Invalid category");
    path = List.copyOf(path);
    if (path.isEmpty() || !path.getLast().equals(id))
      throw new IllegalArgumentException("Category path must end with the category");
  }
}
