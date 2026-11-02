package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.PreservationChecker;
import io.qwenbridge.core.translation.PreservationIssue;
import io.qwenbridge.core.translation.PreservationReport;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CommerceExtraction {
  private CommerceExtraction() {}

  private static final Pattern PRICE =
      Pattern.compile(
          "(?iu)(under|below|less than|up to|at most|over|above|at least|زیر|کمتر از|حداکثر|بیشتر"
              + " از|بالای|unter|unterhalb|moins de|menos de|under|أقل"
              + " من)\\s*([\\d.,]+)\\s*(EUR|euro(?:s)?|یورو|SEK|kronor|کرون|USD|dollars?|دلار|€|\\$)");

  public static List<Constraint> canonical(JsonNode node, String original, String translated) {
    if (node == null || !node.isArray() || node.size() > 32)
      throw new IllegalArgumentException("Invalid constraints");
    List<Constraint> filters = new ArrayList<>();
    Evidence source = new Evidence("original", 0, original.length(), original);
    for (var row : node) {
      if (!row.isArray() || row.size() != 3)
        throw new IllegalArgumentException("Invalid canonical constraint tuple");
      for (var item : row)
        if (!item.isTextual()) throw new IllegalArgumentException("Invalid canonical constraint");
      if (clearedFields(original, translated).contains(row.get(0).asText())) continue;
      filters.add(
          new Constraint(
              row.get(0).asText(),
              Operator.valueOf(row.get(1).asText()),
              row.get(2).asText(),
              source));
    }
    return GroundedCommerceExtraction.complete(original, translated, filters);
  }

  public static String render(String translated, List<Constraint> filters) {
    return GroundedCommerceExtraction.render(translated, filters);
  }

  public static Set<String> clearedFields(String original, String translated) {
    String text = (original + " " + translated).toLowerCase(Locale.ROOT);
    var resets =
        java.util.Map.of(
            "price",
                "no price limit|without (?:a )?price limit|any price|unlimited budget|بدون محدودیت"
                    + " قیمت",
            "brand", "any brand|no brand preference|هر برندی",
            "color", "any colou?r|no colou?r preference|هر رنگی",
            "size", "any size|no size preference|هر سایزی",
            "condition", "any condition|new or refurbished",
            "inStock", "include out of stock|whether in stock or not");
    return resets.entrySet().stream()
        .filter(
            e ->
                Pattern.compile("(?<!\\p{L})(?:" + e.getValue() + ")(?!\\p{L})")
                    .matcher(text)
                    .find())
        .map(java.util.Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  public static List<Constraint> parse(JsonNode node, String original) {
    if (node == null || !node.isArray() || node.size() > 32)
      throw new IllegalArgumentException("Invalid extracted constraints");
    List<Constraint> constraints = new ArrayList<>();
    for (JsonNode row : node) {
      if (!row.isArray() || row.size() != 4)
        throw new IllegalArgumentException("Invalid constraint tuple");
      for (JsonNode item : row)
        if (!item.isTextual() || item.asText().isBlank())
          throw new IllegalArgumentException("Constraint values must be strings");
      String quote = row.get(3).asText();
      int start = original.indexOf(quote);
      if (start < 0) throw new IllegalArgumentException("Model invented source evidence");
      Constraint constraint =
          new Constraint(
              row.get(0).asText(),
              Operator.valueOf(row.get(1).asText()),
              row.get(2).asText(),
              new Evidence("original", start, start + quote.length(), quote));
      if (Set.of("price", "size").contains(constraint.field())) {
        String canonical = normalize(constraint.value());
        if (!normalize(quote).contains(canonical))
          throw new IllegalArgumentException("Numeric value not grounded in original text");
      }
      constraints.add(constraint);
    }
    return List.copyOf(constraints);
  }

  /** Grounded compatibility extraction for old cached analyses; current model output is richer. */
  public static List<Constraint> explicitPrices(String original) {
    String normalized = normalize(original);
    var matcher = PRICE.matcher(normalized);
    List<Constraint> constraints = new ArrayList<>();
    while (matcher.find()) {
      if (matcher.group(2).matches(".*[.,]\\d{3}(?:[.,].*)?"))
        throw new IllegalArgumentException("Ambiguous price separators; use ungrouped digits");
      String comparison = matcher.group(1).toLowerCase(Locale.ROOT);
      Operator operator =
          Set.of("over", "above", "بیشتر از", "بالای").contains(comparison)
              ? Operator.GT
              : comparison.equals("at least")
                  ? Operator.GTE
                  : Set.of("up to", "at most", "حداکثر").contains(comparison)
                      ? Operator.LTE
                      : Operator.LT;
      String currency = currency(matcher.group(3));
      // Digit normalization keeps UTF-16 positions for these supported price expressions.
      if (normalized.length() != original.length()) continue;
      Evidence span =
          new Evidence(
              "original",
              matcher.start(),
              matcher.end(),
              original.substring(matcher.start(), matcher.end()));
      constraints.add(
          new Constraint(
              "price",
              operator,
              new BigDecimal(matcher.group(2).replace(",", ".")).toPlainString(),
              span));
      if (constraints.stream()
          .anyMatch(c -> c.field().equals("currency") && !c.value().equals(currency)))
        throw new IllegalArgumentException("Mixed currencies require clarification");
      constraints.add(new Constraint("currency", Operator.EQ, currency, span));
    }
    return List.copyOf(constraints);
  }

  /** Brand names whose loss in translation is checked (unchanged list, now a Core dictionary). */
  static final EntityDictionary BRANDS =
      new EntityDictionary(
          java.util.stream.Stream.of(
                  "Nike",
                  "Adidas",
                  "Lenovo",
                  "Sony",
                  "Bose",
                  "Apple",
                  "Samsung",
                  "Razer",
                  "Logitech")
              .map(b -> new EntityDictionary.Term("brand", "brand", b, List.of(b)))
              .toList());

  public static Translation translation(
      String original, String language, String target, String translated) {
    return translation(original, language, target, translated, List.of());
  }

  /**
   * Judges the final catalog-language text against the original with the Core preservation check
   * (QB-018). Commerce appends missing constraint phrases to the translation ({@link #render}), so
   * only losses are judged here: numbers, brands, exact identifiers and, for languages whose
   * currency words are known, currencies. Additions and operator or negation checks are applied by
   * the Core stage where text is not rewritten afterwards (the v1 pipeline).
   *
   * @param failures translation attempts that failed; each becomes an issue
   */
  public static Translation translation(
      String original, String language, String target, String translated, List<String> failures) {
    List<String> issues = new ArrayList<>(failures);
    PreservationReport report =
        PreservationChecker.check(
            original,
            LanguageTag.parse(language).orElse(LanguageTag.UNDETERMINED),
            translated,
            LanguageTag.parse(target).orElse(LanguageTag.UNDETERMINED),
            BRANDS);
    for (PreservationIssue issue : report.lost()) {
      String label =
          switch (issue.kind()) {
            case NUMBER -> "number";
            case ENTITY -> "brand";
            case IDENTIFIER -> "identifier";
            case CURRENCY -> "currency";
            default -> null;
          };
      if (label == null) continue;
      for (String subject : issue.subjects()) {
        String shown = subject.startsWith("brand:") ? subject.substring(6) : subject;
        String message = "A source " + label + " was not preserved: " + shown;
        if (!issues.contains(message)) issues.add(message);
      }
    }
    return new Translation(1, original, language, target, translated, issues.isEmpty(), issues);
  }

  public static String currency(String value) {
    String s = value.toLowerCase(Locale.ROOT);
    if (s.matches("eur|euros?|یورو|€")) return "EUR";
    if (s.matches("sek|kronor|کرون")) return "SEK";
    if (s.matches("usd|dollars?|دلار|\\$")) return "USD";
    throw new IllegalArgumentException("Unknown currency");
  }

  public static String normalize(String text) {
    String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int digit = Character.digit(c, 10);
      out.append(digit >= 0 ? (char) ('0' + digit) : c == '٫' ? '.' : c);
    }
    return out.toString();
  }
}
