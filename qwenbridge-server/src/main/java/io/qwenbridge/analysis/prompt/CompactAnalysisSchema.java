package io.qwenbridge.analysis.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal model wire format only. Public REST/SSE and SDK contracts are unchanged. */
final class CompactAnalysisSchema {
  private static final Map<String, Object> SCHEMA = create();

  private CompactAnalysisSchema() {}

  static Map<String, Object> schema() {
    return SCHEMA;
  }

  private static Map<String, Object> create() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("v", Map.of("type", "integer", "enum", List.of(3)));
    fields.put("l", Map.of("type", "string", "pattern", "^([a-z]{2}|unknown)$"));
    fields.put(
        "i",
        Map.of(
            "type",
            "string",
            "enum",
            List.of("PRODUCT_SEARCH", "NAVIGATION", "FILTER", "COMPARE", "UNKNOWN")));
    fields.put(
        "q",
        Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1, "maxItems", 3));
    fields.put("e", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 16));
    fields.put(
        "c",
        Map.of(
            "type",
            "array",
            "items",
            Map.of("type", "number", "minimum", 0, "maximum", 1),
            "minItems",
            3,
            "maxItems",
            3));
    fields.put("t", Map.of("type", "boolean"));
    fields.put(
        "s",
        Map.of(
            "type",
            "string",
            "enum",
            List.of("KEYWORD", "SEMANTIC", "VECTOR", "HYBRID", "DIRECT_ANSWER")));
    fields.put(
        "o",
        Map.of(
            "type",
            "array",
            "items",
            Map.of("type", "string", "enum", List.of("F", "R", "W", "A")),
            "maxItems",
            4));
    fields.put("r", Map.of("type", "string"));
    fields.put(
        "f",
        Map.of(
            "type",
            "array",
            "maxItems",
            32,
            "items",
            Map.of(
                "type", "array", "minItems", 3, "maxItems", 3, "items", Map.of("type", "string"))));
    fields.put("a", Map.of("type", "array", "maxItems", 4, "items", Map.of("type", "string")));
    fields.put("h", Map.of("type", "string", "enum", List.of("ALLOW", "REVIEW", "BLOCK")));
    return Map.of(
        "type",
        "object",
        "properties",
        java.util.Collections.unmodifiableMap(fields),
        "required",
        List.copyOf(fields.keySet()),
        "additionalProperties",
        false);
  }
}
