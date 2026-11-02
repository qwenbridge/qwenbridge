package io.qwenbridge.core.evidence;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A consistent collection of evidence (QB-080): ids are unique, every parent exists and the
 * provenance chain has no cycles, and every user-text span matches the original query.
 */
public record EvidenceSet(List<EvidenceRecord> records) {

  public static final EvidenceSet EMPTY = new EvidenceSet(List.of());

  public EvidenceSet {
    records = List.copyOf(records);
    Map<String, EvidenceRecord> byId = new LinkedHashMap<>();
    for (EvidenceRecord r : records) {
      if (byId.put(r.id(), r) != null) {
        throw new IllegalArgumentException("duplicate evidence id " + r.id());
      }
    }
    for (EvidenceRecord r : records) {
      Set<String> seen = new HashSet<>();
      Optional<String> parent = r.parentId();
      while (parent.isPresent()) {
        if (!seen.add(parent.get())) {
          throw new IllegalArgumentException("evidence provenance cycle at " + r.id());
        }
        EvidenceRecord p = byId.get(parent.get());
        if (p == null) {
          throw new IllegalArgumentException("unknown parent evidence for " + r.id());
        }
        parent = p.parentId();
      }
    }
  }

  public Optional<EvidenceRecord> find(String id) {
    return records.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  /** Whether every user-text span still matches the query it claims to quote. */
  public boolean groundedIn(String originalText) {
    return records.stream()
        .filter(r -> r.kind() == EvidenceRecord.Kind.USER_TEXT)
        .allMatch(r -> r.span().orElseThrow().matches(originalText));
  }
}
