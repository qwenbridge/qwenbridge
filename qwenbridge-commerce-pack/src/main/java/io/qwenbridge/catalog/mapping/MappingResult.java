package io.qwenbridge.catalog.mapping;

import java.util.List;

/** A typed mapping outcome: a draft, or the reasons the record was rejected. */
public sealed interface MappingResult {
  record Mapped(ProductDraft draft) implements MappingResult {
    public Mapped {
      if (draft == null) throw new IllegalArgumentException("Draft required");
    }
  }

  record Rejected(List<MappingIssue> issues) implements MappingResult {
    public Rejected {
      issues = List.copyOf(issues);
      if (issues.isEmpty()) throw new IllegalArgumentException("A rejection needs a reason");
    }
  }

  static MappingResult reject(String path, String message) {
    return new Rejected(List.of(new MappingIssue(path, message)));
  }
}
