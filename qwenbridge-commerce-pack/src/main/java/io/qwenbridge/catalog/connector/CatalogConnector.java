package io.qwenbridge.catalog.connector;

import java.util.List;
import java.util.Map;

/**
 * A catalog source read page by page (QB-093, QB-204). Checkpoints advance only after every change
 * in the page has been acknowledged by the sink. Field meaning belongs to the pack's catalog
 * mappings, not to the transport.
 */
public interface CatalogConnector extends AutoCloseable {
  record Change(String id, boolean deleted, Map<String, Object> document) {
    public Change {
      if (id == null || id.isBlank() || id.length() > 512)
        throw new IllegalArgumentException("Invalid catalog identity");
      document = Map.copyOf(document);
    }
  }

  record Page(List<Change> changes, String nextCursor, boolean hasMore) {
    public Page {
      changes = List.copyOf(changes);
      if (changes.size() > 10000 || (hasMore && (nextCursor == null || nextCursor.isBlank())))
        throw new IllegalArgumentException("Invalid connector page");
    }
  }

  Page fetch(String cursor);

  /**
   * True when every run reads the complete catalog from the start (a snapshot), so records not seen
   * in a finished run were removed at the source; false for change feeds with a cursor.
   */
  default boolean completeSnapshot() {
    return false;
  }

  @Override
  default void close() {}
}
