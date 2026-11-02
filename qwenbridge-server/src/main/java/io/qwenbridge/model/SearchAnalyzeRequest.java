package io.qwenbridge.model;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * v1 analyze request body. Field syntax (language tags, limits, context keys) is validated by the
 * universal request contract ({@code io.qwenbridge.core.request.UniversalRequestParser}) through
 * {@code UniversalRequestGateway}, not by annotations, so every transport shares one rule set.
 *
 * <p>{@code contractVersion}, {@code responseLanguage}, {@code context} and {@code domainHints}
 * were added additively in contract version 1 and are optional; existing clients are unaffected.
 */
public record SearchAnalyzeRequest(
    String requestId,
    @NotBlank(message = "query must not be blank") String query,
    String declaredLanguage,
    String locale,
    Integer contractVersion,
    String responseLanguage,
    Map<String, String> context,
    List<String> domainHints) {

  public SearchAnalyzeRequest {
    // Immutable copies; a JSON null entry means "not provided" and is dropped.
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

  public SearchAnalyzeRequest(String requestId, String query) {
    this(requestId, query, null, null);
  }

  public SearchAnalyzeRequest(
      String requestId, String query, String declaredLanguage, String locale) {
    this(requestId, query, declaredLanguage, locale, null, null, null, null);
  }
}
