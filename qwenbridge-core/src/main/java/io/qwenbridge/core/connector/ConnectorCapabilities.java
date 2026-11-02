package io.qwenbridge.core.connector;

import io.qwenbridge.core.retrieval.RetrievalMode;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a connector can do (QB-154, QB-238): its data access (read, write, delete), its retrieval
 * modes, and whether it streams results. A read-only connector can never be asked to write; the
 * runtime refuses before calling it. Capabilities are data: consumers inspect them without running
 * any operation.
 */
public record ConnectorCapabilities(
    Set<Access> access, Set<RetrievalMode> modes, boolean streaming) {
  public enum Access {
    READ,
    WRITE,
    DELETE;

    /** Whether the access changes data; such operations also need explicit authorization. */
    public boolean mutating() {
      return this != READ;
    }
  }

  public ConnectorCapabilities {
    access = access == null || access.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(access));
    modes = modes == null || modes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(modes));
  }

  /** A connector that does not stream (all built-in connectors in 1.0). */
  public ConnectorCapabilities(Set<Access> access, Set<RetrievalMode> modes) {
    this(access, modes, false);
  }

  public boolean allows(Access a) {
    return access.contains(a);
  }

  public boolean readOnly() {
    return access.equals(Set.of(Access.READ));
  }

  /** Whether everything this record allows is also allowed by {@code declared}. */
  public boolean within(ConnectorCapabilities declared) {
    return declared.access.containsAll(access)
        && declared.modes.containsAll(modes)
        && (!streaming || declared.streaming);
  }
}
