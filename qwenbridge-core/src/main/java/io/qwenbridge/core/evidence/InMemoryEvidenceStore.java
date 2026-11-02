package io.qwenbridge.core.evidence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A process-local {@link EvidenceStore}; the default when no connector provides storage. */
public final class InMemoryEvidenceStore implements EvidenceStore {

  private final Map<String, EvidenceRecord> records = new ConcurrentHashMap<>();

  @Override
  public void put(String tenant, EvidenceRecord record) {
    Objects.requireNonNull(tenant, "tenant must not be null");
    records.put(tenant + "\u0000" + record.id(), record);
  }

  @Override
  public Optional<EvidenceRecord> get(String tenant, String id) {
    return Optional.ofNullable(records.get(tenant + "\u0000" + id));
  }
}
