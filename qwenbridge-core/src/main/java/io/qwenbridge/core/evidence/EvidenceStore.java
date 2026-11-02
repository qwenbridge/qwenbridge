package io.qwenbridge.core.evidence;

import java.util.List;
import java.util.Optional;

/**
 * Optional storage for evidence (QB-080). Core never requires one; the server provides an
 * in-process store and an optional Redis store. Records are always scoped to a tenant.
 */
public interface EvidenceStore {

  void put(String tenant, EvidenceRecord record);

  Optional<EvidenceRecord> get(String tenant, String id);

  /** The record and its ancestors, nearest first. */
  default List<EvidenceRecord> provenance(String tenant, String id) {
    java.util.List<EvidenceRecord> chain = new java.util.ArrayList<>();
    Optional<EvidenceRecord> current = get(tenant, id);
    java.util.Set<String> seen = new java.util.HashSet<>();
    while (current.isPresent() && seen.add(current.get().id())) {
      chain.add(current.get());
      current = current.get().parentId().flatMap(p -> get(tenant, p));
    }
    return List.copyOf(chain);
  }
}
