package io.qwenbridge.extensions;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Every tool-boundary security decision (QB-231): who, which tool and operation, the outcome and
 * the stable reason codes. Tool inputs and outputs are never recorded, so secrets passed to tools
 * cannot leak through the audit trail.
 */
@jakarta.inject.Singleton
public final class ToolAudit {
  private static final Logger LOG = Logger.getLogger(ToolAudit.class);
  private static final int CAPACITY = 2_000;

  public record Entry(
      Instant at,
      String runId,
      String tenant,
      String subject,
      String tool,
      String operation,
      String outcome,
      List<String> reasons) {
    public Entry {
      reasons = List.copyOf(reasons);
    }
  }

  private final Deque<Entry> entries = new ArrayDeque<>();

  public synchronized void record(Entry entry) {
    entries.addFirst(entry);
    while (entries.size() > CAPACITY) entries.removeLast();
    LOG.infof(
        "tool-decision run=%s tenant=%s subject=%s tool=%s operation=%s outcome=%s reasons=%s",
        entry.runId(),
        entry.tenant(),
        entry.subject(),
        entry.tool(),
        entry.operation(),
        entry.outcome(),
        entry.reasons());
  }

  /** The newest entries for a tenant (never another tenant's). */
  public synchronized List<Entry> recent(String tenant, int limit) {
    return entries.stream().filter(e -> e.tenant().equals(tenant)).limit(limit).toList();
  }
}
