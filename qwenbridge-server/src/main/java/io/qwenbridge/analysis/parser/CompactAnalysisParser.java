package io.qwenbridge.analysis.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strictly validates the compact model format before expanding the existing domain contract. */
final class CompactAnalysisParser {
  private static final Set<String> FIELDS =
      Set.of("v", "l", "i", "q", "e", "c", "t", "s", "o", "r");

  private CompactAnalysisParser() {}

  static SearchAnalysis parse(JsonNode root, String originalQuery) {
    Set<String> keys = new HashSet<>();
    root.fieldNames().forEachRemaining(keys::add);
    boolean canonical = root.path("v").asInt() == 3;
    boolean extended = root.path("v").asInt() == 2 || canonical;
    Set<String> required = new HashSet<>(FIELDS);
    if (extended) required.addAll(Set.of("f", "a", "h"));
    require(keys.equals(required), "Invalid compact fields");
    require(
        root.get("v").isIntegralNumber()
            && root.get("v").canConvertToInt()
            && (root.get("v").intValue() == 1 || extended),
        "Unsupported compact version");
    String language = text(root, "l", 7);
    require(language.matches("[a-z]{2}|unknown"), "Invalid language code");
    IntentType intent = IntentType.valueOf(text(root, "i", 32));
    SearchMode mode = SearchMode.valueOf(text(root, "s", 32));
    List<String> rewrites = strings(root.get("q"), 1, 3, 2048);
    List<String> entities = strings(root.get("e"), 0, 16, 256);
    JsonNode scores = root.get("c");
    require(scores.isArray() && scores.size() == 3, "Three confidence signals required");
    double[] confidence = new double[3];
    for (int index = 0; index < confidence.length; index++) {
      JsonNode score = scores.get(index);
      require(score.isNumber(), "Confidence must be numeric");
      double value = score.doubleValue();
      require(Double.isFinite(value) && value >= 0 && value <= 1, "Invalid confidence");
      confidence[index] = value;
    }
    require(root.get("t").isBoolean(), "Semantic validation must be boolean");
    List<String> operations = strings(root.get("o"), 0, 4, 1);
    require(
        new HashSet<>(operations).size() == operations.size()
            && Set.of("F", "R", "W", "A").containsAll(operations),
        "Invalid operations");
    String reason = text(root, "r", 256);
    boolean answer = mode == SearchMode.DIRECT_ANSWER || operations.contains("A");
    var questions =
        new ArrayList<>(extended ? strings(root.get("a"), 0, 4, 512) : List.<String>of());
    List<io.qwenbridge.analysis.model.Grounded.Constraint> constraints = List.of();
    if (extended) {
      try {
        constraints =
            canonical
                ? io.qwenbridge.commerce.CommerceExtraction.canonical(
                    root.get("f"), originalQuery, rewrites.getFirst())
                : io.qwenbridge.commerce.CommerceExtraction.parse(root.get("f"), originalQuery);
      } catch (IllegalArgumentException ex) {
        questions.add("Please clarify unsupported or invalid constraints: " + ex.getMessage());
      }
    }
    return SearchAnalysis.builder()
        .language(language)
        .intent(intent)
        .intentConfidence(confidence[0])
        .intentReason(reason)
        .rewrites(rewrites)
        .semanticValidated(root.get("t").booleanValue())
        .semanticScore(confidence[1])
        .semanticMeaning(rewrites.getFirst())
        .entities(entities)
        .searchMode(answer ? SearchMode.DIRECT_ANSWER : mode)
        .backend(answer ? SearchBackend.NONE : SearchBackend.OPENSEARCH)
        .keywordSearch(!answer && mode == SearchMode.KEYWORD)
        .vectorSearch(!answer && (mode == SearchMode.VECTOR || mode == SearchMode.SEMANTIC))
        .hybridSearch(!answer && mode == SearchMode.HYBRID)
        .facets(operations.contains("F"))
        .rerank(operations.contains("R"))
        .rewriteAgain(operations.contains("W"))
        .answer(answer)
        .decisionConfidence(confidence[2])
        .decisionReason(reason)
        .constraints(constraints)
        .ambiguities(questions)
        .modelThreatDecision(extended ? threat(root) : "UNAVAILABLE")
        .build();
  }

  private static String threat(JsonNode root) {
    String result = text(root, "h", 16);
    require(Set.of("ALLOW", "BLOCK", "REVIEW").contains(result), "Invalid model threat decision");
    return result;
  }

  private static String text(JsonNode root, String name, int maxLength) {
    JsonNode value = root.get(name);
    if (value == null) throw new IllegalArgumentException("Missing " + name);
    require(
        value.isTextual()
            && !value.textValue().isBlank()
            && value.textValue().length() <= maxLength,
        "Invalid " + name);
    return value.textValue();
  }

  private static List<String> strings(JsonNode node, int minimum, int maximum, int maxLength) {
    if (node == null) throw new IllegalArgumentException("Missing list");
    require(
        node.isArray() && node.size() >= minimum && node.size() <= maximum, "Invalid list size");
    List<String> result = new ArrayList<>();
    for (JsonNode value : node) {
      require(
          value.isTextual()
              && !value.textValue().isBlank()
              && value.textValue().length() <= maxLength,
          "Invalid list value");
      result.add(value.textValue());
    }
    return List.copyOf(result);
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
