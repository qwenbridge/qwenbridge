package io.qwenbridge.intelligence.runtime;

import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Conservative fail-closed resource limits for opt-in structured execution. */
public final class StructuredExecutionLimits {
  public static final int SUPPORTED_IR_VERSION = 1;
  public static final int MAX_QUERY_LENGTH = 2048;
  public static final int MAX_PRICE_BOUNDS = 16;
  public static final int MAX_ENTITIES = 64;
  public static final int MAX_AMOUNT_PRECISION = 18;
  public static final int MAX_AMOUNT_SCALE = 6;
  public static final int MAX_ENTITY_VALUE_LENGTH = 256;
  public static final int MAX_ENTITY_TYPE_LENGTH = 64;
  public static final int MAX_TOTAL_ENTITY_EVIDENCE_LENGTH = 1024;
  // Stages 33-37: additional bounded-work budgets for structured execution.
  public static final int MAX_QUERY_UTF8_BYTES = 4096;
  public static final int MAX_DISTINCT_ENTITY_TYPES = 16;
  public static final int MAX_DISTINCT_CURRENCIES = 8;
  public static final int MAX_TOTAL_ENTITY_TYPE_LENGTH = 1024;
  public static final BigDecimal MAX_PRICE_AMOUNT = new BigDecimal("1000000000000");

  // Stage 38-42: resource safety and unambiguous structured execution.
  public static final int MAX_QUERY_CODE_POINTS = 1536;
  public static final int MAX_ENTITY_VALUE_UTF8_BYTES = 512;

  // Stages 43-50: bounded input complexity and strict evidence hygiene.
  public static final int MAX_QUERY_LINES = 32;
  public static final int MAX_QUERY_TABS = 64;
  public static final int MAX_CONSECUTIVE_COMBINING_MARKS = 16;
  public static final int MAX_PRICE_TEXT_BYTES = 256;
  public static final int MAX_TOTAL_CONSTRAINTS = 20;

  private StructuredExecutionLimits() {}

  public static void validate(LogicalQueryPlan plan) {
    Objects.requireNonNull(plan, "plan");
    // Stage 23: refuse unknown IR schemas instead of silently executing incompatible plans.
    if (plan.irVersion() != SUPPORTED_IR_VERSION) {
      throw new IllegalArgumentException("Unsupported structured query IR version");
    }
    // Stage 19: reject oversized input before scanning code points or allocating backend requests.
    if (plan.originalQuery().length() > MAX_QUERY_LENGTH) {
      throw new IllegalArgumentException("Structured query exceeds maximum length");
    }
    // Stage 33: bound encoded backend payload size as well as UTF-16 input length.
    if (plan.originalQuery().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        > MAX_QUERY_UTF8_BYTES) {
      throw new IllegalArgumentException("Structured query exceeds UTF-8 byte budget");
    }
    // Reject invisible control and formatting characters before sending a query to the backend.
    // Ordinary spaces, tabs, and line breaks are allowed; malformed UTF-16 is not.
    String query = plan.originalQuery();
    int lines = 1;
    int tabs = 0;
    int consecutiveMarks = 0;
    for (int offset = 0; offset < query.length(); ) {
      int codePoint = query.codePointAt(offset);
      if (Character.isSurrogate(query.charAt(offset))
          && (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
        throw new IllegalArgumentException("Structured query contains malformed Unicode");
      }
      // Stage 24: Unicode noncharacters are not valid query text for backend execution.
      if ((codePoint >= 0xFDD0 && codePoint <= 0xFDEF) || (codePoint & 0xFFFE) == 0xFFFE) {
        throw new IllegalArgumentException("Structured query contains Unicode noncharacter");
      }
      if ((Character.isISOControl(codePoint)
              && codePoint != '\t'
              && codePoint != '\n'
              && codePoint != '\r')
          || Character.getType(codePoint) == Character.FORMAT) {
        throw new IllegalArgumentException(
            "Structured query contains disallowed control or format characters");
      }
      // Stage 43: bound multi-line payloads (CRLF counts as one line).
      if (codePoint == '\n' && (offset == 0 || query.charAt(offset - 1) != '\r')) lines++;
      if (codePoint == '\r') lines++;
      if (lines > MAX_QUERY_LINES)
        throw new IllegalArgumentException("Too many structured query lines");
      // Stage 44: bound tab-delimited input complexity.
      if (codePoint == '\t' && ++tabs > MAX_QUERY_TABS) {
        throw new IllegalArgumentException("Too many structured query tabs");
      }
      // Stage 45: limit pathological combining-mark sequences, retaining ordinary accents.
      int kind = Character.getType(codePoint);
      if (kind == Character.NON_SPACING_MARK
          || kind == Character.COMBINING_SPACING_MARK
          || kind == Character.ENCLOSING_MARK) {
        if (++consecutiveMarks > MAX_CONSECUTIVE_COMBINING_MARKS) {
          throw new IllegalArgumentException("Excessive combining marks");
        }
      } else consecutiveMarks = 0;
      // Stage 46: private-use characters have no stable cross-system semantics.
      if (kind == Character.PRIVATE_USE) {
        throw new IllegalArgumentException("Private-use code point in structured query");
      }
      // Stage 47: disallow undefined Unicode code points in structured execution.
      if (kind == Character.UNASSIGNED) {
        throw new IllegalArgumentException("Unassigned code point in structured query");
      }
      offset += Character.charCount(codePoint);
    }
    // Stage 38: bound Unicode code-point processing independently of UTF-16 length.
    if (query.codePointCount(0, query.length()) > MAX_QUERY_CODE_POINTS) {
      throw new IllegalArgumentException("Structured query exceeds code point budget");
    }
    // Stage 50: bound total filter complexity across entity and price filters.
    if ((long) plan.entities().size() + plan.priceUpperBounds().size() > MAX_TOTAL_CONSTRAINTS) {
      throw new IllegalArgumentException("Too many combined structured constraints");
    }
    if (plan.priceUpperBounds().size() > MAX_PRICE_BOUNDS
        || plan.entities().size() > MAX_ENTITIES) {
      throw new IllegalArgumentException("Structured query exceeds constraint budget");
    }
    // Stage 20: every entity must be grounded at the exact UTF-16 evidence span.
    // Stage 27: bound aggregate evidence work, not only per-entity length.
    int evidenceLength = 0;
    int entityTypeLength = 0;
    Set<String> entityTypes = new HashSet<>();
    // Stage 39: entity classification identifiers are case-sensitive downstream;
    // reject case-folding collisions to avoid ambiguous interpretations.
    Set<String> foldedEntityTypes = new HashSet<>();
    // Stage 28: reject duplicate evidence spans before backend execution.
    Set<String> entitySpans = new HashSet<>();
    // Stage 31: evidence spans may not overlap; overlapping extraction is ambiguous.
    int previousEnd = -1;
    var orderedEntities = new java.util.ArrayList<>(plan.entities());
    orderedEntities.sort(java.util.Comparator.comparingInt(e -> e.start()));
    for (var entity : orderedEntities) {
      if (entity.start() < previousEnd) {
        throw new IllegalArgumentException("Overlapping structured entity evidence");
      }
      previousEnd = entity.end();
      // Stage 48: blank evidence is not a meaningful extraction even when grounded.
      if (entity.value().isBlank()) {
        throw new IllegalArgumentException("Blank structured entity evidence");
      }
      // Stage 32: entity type metadata must be a bounded ASCII identifier.
      if (!entity.type().matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
        throw new IllegalArgumentException("Invalid structured entity type identifier");
      }
      // Stage 34: bound distinct entity classifications to constrain downstream fanout.
      if (!entityTypes.contains(entity.type())
          && !foldedEntityTypes.add(entity.type().toLowerCase(java.util.Locale.ROOT))) {
        throw new IllegalArgumentException("Ambiguous entity type casing");
      }
      entityTypes.add(entity.type());
      if (entityTypes.size() > MAX_DISTINCT_ENTITY_TYPES) {
        throw new IllegalArgumentException("Too many distinct structured entity types");
      }
      // Stage 37: cap aggregate entity type metadata, not only each identifier.
      entityTypeLength += entity.type().length();
      if (entityTypeLength > MAX_TOTAL_ENTITY_TYPE_LENGTH) {
        throw new IllegalArgumentException("Structured entity type metadata exceeds budget");
      }
      // Stage 40: bound per-entity UTF-8 evidence bytes before downstream serialization.
      if (entity.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
          > MAX_ENTITY_VALUE_UTF8_BYTES) {
        throw new IllegalArgumentException("Structured entity exceeds UTF-8 evidence budget");
      }
      evidenceLength += entity.value().length();
      if (evidenceLength > MAX_TOTAL_ENTITY_EVIDENCE_LENGTH) {
        throw new IllegalArgumentException("Structured entity evidence exceeds total budget");
      }
      if (!entitySpans.add(entity.start() + ":" + entity.end() + ":" + entity.type())) {
        throw new IllegalArgumentException("Duplicate structured entity evidence");
      }
      // Stage 25: entity spans must not cut through a supplementary code point.
      if ((entity.start() > 0
              && entity.start() < query.length()
              && Character.isLowSurrogate(query.charAt(entity.start()))
              && Character.isHighSurrogate(query.charAt(entity.start() - 1)))
          || (entity.end() > 0
              && entity.end() < query.length()
              && Character.isLowSurrogate(query.charAt(entity.end()))
              && Character.isHighSurrogate(query.charAt(entity.end() - 1)))) {
        throw new IllegalArgumentException("Structured entity splits a Unicode code point");
      }
      if (entity.value().length() > MAX_ENTITY_VALUE_LENGTH
          || entity.type().length() > MAX_ENTITY_TYPE_LENGTH
          || entity.end() > query.length()
          || !query.regionMatches(entity.start(), entity.value(), 0, entity.value().length())
          || entity.end() - entity.start() != entity.value().length()) {
        throw new IllegalArgumentException("Structured entity evidence is invalid");
      }
    }
    // Stage 29: reject identical price filters rather than sending redundant DSL clauses.
    Set<LogicalQueryPlan.PriceUpperBound> uniqueBounds = new HashSet<>();
    Set<String> distinctCurrencies = new HashSet<>();
    // Stage 41: multiple different bounds for the same currency/operator are ambiguous.
    Set<String> currencyOperatorPairs = new HashSet<>();
    int priceTextBytes = 0;
    for (var bound : plan.priceUpperBounds()) {
      // Stage 49: cap aggregate decimal serialization overhead.
      priceTextBytes +=
          bound.amount().toPlainString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      if (priceTextBytes > MAX_PRICE_TEXT_BYTES) {
        throw new IllegalArgumentException("Structured price text exceeds budget");
      }
      if (!uniqueBounds.add(bound)) {
        throw new IllegalArgumentException("Duplicate structured price constraint");
      }
      // Never send malformed or non-canonical currency codes to OpenSearch.
      // The mapping stores uppercase ISO 4217 identifiers (for example SEK).
      String code = bound.currency();
      if (!currencyOperatorPairs.add(code + ":" + bound.operator().name())) {
        throw new IllegalArgumentException("Conflicting structured price constraints");
      }
      // Stage 35: bound currency-specific filter fanout.
      distinctCurrencies.add(code);
      if (distinctCurrencies.size() > MAX_DISTINCT_CURRENCIES) {
        throw new IllegalArgumentException("Too many distinct structured currencies");
      }
      if (!code.matches("[A-Z]{3}")) {
        throw new IllegalArgumentException("Structured currency must be uppercase ISO 4217");
      }
      try {
        Currency.getInstance(code);
      } catch (IllegalArgumentException invalidCurrency) {
        throw new IllegalArgumentException("Unsupported structured currency", invalidCurrency);
      }
      // Stage 21: reject noncanonical field names and negative-scale scientific notation.
      // Stage 30: reject decimal amounts with fractional digits beyond the currency minor unit.
      // Do not round monetary constraints silently: that can change search semantics.
      int minorUnits = Currency.getInstance(code).getDefaultFractionDigits();
      if (minorUnits >= 0 && bound.amount().stripTrailingZeros().scale() > minorUnits) {
        throw new IllegalArgumentException("Structured price exceeds currency minor units");
      }
      // Stage 42: never accept a price bound of zero with a strict less-than operator;
      // monetary amounts are nonnegative and the predicate is unsatisfiable.
      if (bound.amount().signum() == 0
          && bound.operator() == io.qwenbridge.intelligence.ir.QueryOperator.LESS_THAN) {
        throw new IllegalArgumentException("Unsatisfiable structured price upper bound");
      }
      if (!"price".equals(bound.field())) {
        throw new IllegalArgumentException("Unsupported structured price field");
      }
      BigDecimal amount = bound.amount();
      // Stage 36: reject unreasonable monetary upper bounds before backend execution.
      if (amount.compareTo(MAX_PRICE_AMOUNT) > 0) {
        throw new IllegalArgumentException("Structured price exceeds maximum amount");
      }
      if (amount.scale() < 0) {
        throw new IllegalArgumentException("Structured price must use ordinary decimal notation");
      }
      if (amount.precision() > MAX_AMOUNT_PRECISION || amount.scale() > MAX_AMOUNT_SCALE) {
        throw new IllegalArgumentException("Structured price exceeds numeric budget");
      }
    }
  }
}
