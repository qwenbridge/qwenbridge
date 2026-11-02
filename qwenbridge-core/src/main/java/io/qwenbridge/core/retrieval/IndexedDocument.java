package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.Map;
import java.util.Objects;

/**
 * A document as stored by an in-memory index (QB-108).
 *
 * @param text the text that is tokenized for keyword matching
 * @param fields typed fields used by filters and returned with results
 */
public record IndexedDocument(
    String tenant, String id, String text, Map<String, FieldValue> fields) {

  public IndexedDocument {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(text, "text must not be null");
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }
}
