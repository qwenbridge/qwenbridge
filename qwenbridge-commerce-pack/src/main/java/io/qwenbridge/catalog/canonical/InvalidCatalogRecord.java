package io.qwenbridge.catalog.canonical;

import io.qwenbridge.catalog.mapping.MappingIssue;
import java.util.List;

/** A source record that cannot become a canonical product; carries the source paths at fault. */
public final class InvalidCatalogRecord extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final transient List<MappingIssue> issues;

  public InvalidCatalogRecord(List<MappingIssue> issues) {
    super(issues.isEmpty() ? "Invalid catalog record" : issues.getFirst().message());
    this.issues = List.copyOf(issues);
  }

  public InvalidCatalogRecord(String path, String message) {
    this(List.of(new MappingIssue(path, message)));
  }

  public List<MappingIssue> issues() {
    return issues;
  }
}
