package io.qwenbridge.core.suggestion;

import java.util.List;
import java.util.Objects;

/** The versioned suggestion contract (QB-132). */
public record SuggestionSet(String version, String baseRequestId, List<Suggestion> items) {
  public static final String VERSION = "suggestions/1.0";

  public SuggestionSet {
    Objects.requireNonNull(version, "version must not be null");
    Objects.requireNonNull(baseRequestId, "baseRequestId must not be null");
    items = List.copyOf(items);
  }
}
