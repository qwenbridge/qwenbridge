package io.qwenbridge.intelligence.extraction;

import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative extraction: never turns model-only entity values into trusted filters. */
@ApplicationScoped
public class QueryEntityConstraintExtractor {
  // Deliberately limited to unambiguous English syntax and explicit currency tokens.
  private static final Pattern MAX_PRICE =
      Pattern.compile(
          "(?i)\\b(under|below|up"
              + " to)\\s+(?:(USD|EUR|SEK)\\s+)?([0-9]+(?:\\.[0-9]{1,2})?)\\s*(USD|EUR|SEK)?\\b");

  public ExtractedQuery extract(SemanticAnalysis analysis) {
    Objects.requireNonNull(analysis, "analysis");
    String query = analysis.originalQuery();
    String lowerQuery = query.toLowerCase(Locale.ROOT);
    List<ExtractedQuery.GroundedEntity> entities = new ArrayList<>();
    for (SemanticEntity entity : analysis.entities()) {
      String value = entity.value();
      int from = 0;
      while (from < query.length()) {
        int index = lowerQuery.indexOf(value.toLowerCase(Locale.ROOT), from);
        if (index < 0) break;
        int end = index + value.length();
        // Require token boundaries to avoid treating a substring of a different word as evidence.
        boolean left = index == 0 || !Character.isLetterOrDigit(query.charAt(index - 1));
        boolean right = end == query.length() || !Character.isLetterOrDigit(query.charAt(end));
        if (left && right) {
          entities.add(
              new ExtractedQuery.GroundedEntity(
                  query.substring(index, end), entity.type().name(), index, end));
          break;
        }
        from = index + 1;
      }
    }
    List<ExtractedQuery.PriceConstraint> constraints = new ArrayList<>();
    Matcher matcher = MAX_PRICE.matcher(query);
    while (matcher.find()) {
      String prefixCurrency = matcher.group(2);
      String suffixCurrency = matcher.group(4);
      if (prefixCurrency == null && suffixCurrency == null) continue;
      if (prefixCurrency != null
          && suffixCurrency != null
          && !prefixCurrency.equalsIgnoreCase(suffixCurrency)) continue;
      String currency =
          (prefixCurrency != null ? prefixCurrency : suffixCurrency).toUpperCase(Locale.ROOT);
      BigDecimal amount = new BigDecimal(matcher.group(3));
      ExtractedQuery.Operator operator =
          matcher.group(1).equalsIgnoreCase("up to")
              ? ExtractedQuery.Operator.LESS_THAN_OR_EQUAL
              : ExtractedQuery.Operator.LESS_THAN;
      constraints.add(
          new ExtractedQuery.PriceConstraint(
              operator, amount, currency, matcher.start(), matcher.end()));
    }
    return new ExtractedQuery(entities, constraints);
  }
}
