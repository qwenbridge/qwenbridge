package io.qwenbridge.core.request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Untrusted request values exactly as received from a transport (REST body, SDK, SSE). Nothing in
 * here has been validated; convert it with {@link UniversalRequestParser}. Every field may be null.
 *
 * <p>Collections are copied immutably. Following JSON optional-value semantics, a {@code null}
 * context value or domain hint means "not provided" and the entry is dropped; it is never turned
 * into an empty string or any other invented value.
 */
public record RawRequest(
    Integer contractVersion,
    String requestId,
    String query,
    String declaredLanguage,
    String responseLanguage,
    String locale,
    Map<String, String> context,
    List<String> domainHints) {

  public RawRequest {
    context = context == null ? null : Map.copyOf(withoutNulls(context));
    domainHints =
        domainHints == null
            ? null
            : List.copyOf(domainHints.stream().filter(Objects::nonNull).toList());
  }

  private static Map<String, String> withoutNulls(Map<String, String> source) {
    Map<String, String> kept = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> {
          if (key != null && value != null) {
            kept.put(key, value);
          }
        });
    return kept;
  }
}
