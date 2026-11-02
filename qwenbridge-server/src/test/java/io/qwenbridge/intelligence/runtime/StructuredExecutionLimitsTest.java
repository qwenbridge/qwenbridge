package io.qwenbridge.intelligence.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredExecutionLimitsTest {
  private static LogicalQueryPlan plan(String query, BigDecimal amount) {
    return new LogicalQueryPlan(
        1,
        query,
        List.of(),
        List.of(
            new LogicalQueryPlan.PriceUpperBound("price", QueryOperator.LESS_THAN, amount, "SEK")));
  }

  @Test
  void rejectsEntityEvidenceBeyondQuery() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 999);
    var plan = new LogicalQueryPlan(1, "mouse", List.of(entity), List.of());
    assertThrows(IllegalArgumentException.class, () -> StructuredExecutionLimits.validate(plan));
  }

  @Test
  void rejectsEntityEvidenceMismatch() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("keyboard", "PRODUCT", 0, 8);
    var plan = new LogicalQueryPlan(1, "mousepad", List.of(entity), List.of());
    assertThrows(IllegalArgumentException.class, () -> StructuredExecutionLimits.validate(plan));
  }

  @Test
  void acceptsGroundedEntity() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("موس", "PRODUCT", 0, 3);
    var plan = new LogicalQueryPlan(1, "موس زیر ۱۰۰", List.of(entity), List.of());
    assertDoesNotThrow(() -> StructuredExecutionLimits.validate(plan));
  }

  @Test
  void rejectsUnsupportedPriceField() {
    var bound =
        new LogicalQueryPlan.PriceUpperBound(
            "admin", QueryOperator.LESS_THAN, BigDecimal.ONE, "SEK");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(), List.of(bound))));
  }

  @Test
  void rejectsNegativeScalePrice() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("1E+3"))));
  }

  @Test
  void rejectsExcessivelyLongEntityValue() {
    String query = "x".repeat(257);
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity(query, "PRODUCT", 0, query.length());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, List.of(entity), List.of())));
  }

  @Test
  void rejectsOversizedInputBeforeEntityProcessing() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "x".repeat(2049), List.of(), List.of())));
  }

  @Test
  void acceptsOrdinaryBound() {
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(plan("mouse under 100 SEK", new BigDecimal("100"))));
  }

  @Test
  void rejectsOversizedQuery() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("a".repeat(2049), BigDecimal.ONE)));
  }

  @Test
  void rejectsExcessiveNumericPrecision() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                plan("mouse", new BigDecimal("1234567890123456789"))));
  }

  @Test
  void rejectsExcessiveNumericScale() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("1.1234567"))));
  }

  @Test
  void rejectsExcessiveConstraintCount() {
    var bound =
        new LogicalQueryPlan.PriceUpperBound(
            "price", QueryOperator.LESS_THAN, BigDecimal.ONE, "SEK");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(
                    1, "mouse", List.of(), java.util.Collections.nCopies(17, bound))));
  }

  private static LogicalQueryPlan currencyPlan(String code) {
    return new LogicalQueryPlan(
        1,
        "mouse under 100 " + code,
        List.of(),
        List.of(
            new LogicalQueryPlan.PriceUpperBound(
                "price", QueryOperator.LESS_THAN, new BigDecimal("100"), code)));
  }

  @Test
  void acceptsOtherIsoCurrency() {
    assertDoesNotThrow(() -> StructuredExecutionLimits.validate(currencyPlan("EUR")));
  }

  @Test
  void rejectsLowercaseCurrency() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(currencyPlan("sek")));
  }

  @Test
  void rejectsUnknownCurrency() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(currencyPlan("ZZZ")));
  }

  @Test
  void rejectsCurrencyWithWhitespace() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(currencyPlan("SEK ")));
  }

  @Test
  void rejectsNullByteInQuery() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse\u0000under 100 SEK", BigDecimal.ONE)));
  }

  @Test
  void rejectsBidirectionalOverride() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse\u202Eunder 100 SEK", BigDecimal.ONE)));
  }

  @Test
  void rejectsUnpairedSurrogate() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse\uD800", BigDecimal.ONE)));
  }

  @Test
  void acceptsNormalMultilingualQuery() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("موس زیر ۱۰۰ SEK", BigDecimal.ONE)));
  }

  @Test
  void acceptsOrdinaryLineBreak() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("mouse\nunder 100 SEK", BigDecimal.ONE)));
  }

  @Test
  void rejectsUnknownIrVersion() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(2, "mouse", List.of(), List.of())));
  }

  @Test
  void rejectsUnicodeNoncharacter() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse\uFDD0", BigDecimal.ONE)));
  }

  @Test
  void rejectsSupplementaryNoncharacter() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                plan("mouse" + new String(Character.toChars(0x10FFFF)), BigDecimal.ONE)));
  }

  @Test
  void acceptsSupplementaryCharacterInQuery() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("mouse \uD83D\uDE80", BigDecimal.ONE)));
  }

  @Test
  void rejectsEntityEndingInsideSurrogatePair() {
    String query = "mouse \uD83D\uDE80";
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("mouse \uD83D", "PRODUCT", 0, 7);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, List.of(entity), List.of())));
  }

  @Test
  void rejectsAggregateEntityEvidenceBudget() {
    String query = "a".repeat(1300);
    var entities = new java.util.ArrayList<io.qwenbridge.intelligence.ir.QueryEntity>();
    for (int i = 0; i < 5; i++) {
      entities.add(
          new io.qwenbridge.intelligence.ir.QueryEntity(
              "a".repeat(250), "PRODUCT", i * 250, (i + 1) * 250));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, entities, List.of())));
  }

  @Test
  void rejectsDuplicateEntityEvidence() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 5);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(entity, entity), List.of())));
  }

  @Test
  void rejectsDuplicatePriceBounds() {
    var bound =
        new LogicalQueryPlan.PriceUpperBound(
            "price", QueryOperator.LESS_THAN, BigDecimal.TEN, "SEK");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(), List.of(bound, bound))));
  }

  @Test
  void rejectsExcessCurrencyFractionDigits() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("10.001"))));
  }

  @Test
  void acceptsTrailingZeroFractionDigits() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("10.000"))));
  }

  @Test
  void acceptsDistinctEntitySpans() {
    var first = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 5);
    var second = new io.qwenbridge.intelligence.ir.QueryEntity("keyboard", "PRODUCT", 6, 14);
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse keyboard", List.of(first, second), List.of())));
  }

  @Test
  void rejectsOverlappingEntityEvidence() {
    var a = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 5);
    var b = new io.qwenbridge.intelligence.ir.QueryEntity("use", "PRODUCT", 2, 5);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(a, b), List.of())));
  }

  @Test
  void acceptsOutOfOrderNonOverlappingEntities() {
    var first = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 5);
    var second = new io.qwenbridge.intelligence.ir.QueryEntity("keyboard", "PRODUCT", 6, 14);
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse keyboard", List.of(second, first), List.of())));
  }

  @Test
  void rejectsEntityTypeWithControlCharacter() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT\nADMIN", 0, 5);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(entity), List.of())));
  }

  @Test
  void acceptsUnderscoreEntityType() {
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT_TYPE", 0, 5);
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(entity), List.of())));
  }

  @Test
  void rejectsExcessUtf8Bytes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "€".repeat(1500), List.of(), List.of())));
  }

  @Test
  void acceptsUtf8WithinBudget() {
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "م".repeat(1200), List.of(), List.of())));
  }

  @Test
  void rejectsTooManyDistinctEntityTypes() {
    String query = "x ".repeat(17);
    var entities = new java.util.ArrayList<io.qwenbridge.intelligence.ir.QueryEntity>();
    for (int i = 0; i < 17; i++) {
      entities.add(
          new io.qwenbridge.intelligence.ir.QueryEntity("x", "TYPE" + i, i * 2, i * 2 + 1));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, entities, List.of())));
  }

  @Test
  void rejectsTooManyCurrencies() {
    String[] codes = {"SEK", "EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD"};
    var bounds = new java.util.ArrayList<LogicalQueryPlan.PriceUpperBound>();
    for (String code : codes) {
      bounds.add(
          new LogicalQueryPlan.PriceUpperBound(
              "price", QueryOperator.LESS_THAN, BigDecimal.TEN, code));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(), bounds)));
  }

  @Test
  void rejectsExcessivePriceAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("1000000000001"))));
  }

  @Test
  void acceptsPriceAtMaximumAmount() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("1000000000000"))));
  }

  @Test
  void rejectsExcessiveTotalEntityTypeMetadata() {
    String query = "x ".repeat(20);
    var entities = new java.util.ArrayList<io.qwenbridge.intelligence.ir.QueryEntity>();
    for (int i = 0; i < 20; i++) {
      entities.add(
          new io.qwenbridge.intelligence.ir.QueryEntity(
              "x", "T" + "A".repeat(62), i * 2, i * 2 + 1));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, entities, List.of())));
  }

  // Stage 38: code point budget is independent of UTF-8 byte and UTF-16 budgets.
  @Test
  void rejectsExcessQueryCodePoints() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "a".repeat(1537), List.of(), List.of())));
  }

  // Stage 39: conflicting case variants must not create ambiguous type dispatch.
  @Test
  void rejectsEntityTypeCaseCollision() {
    var entities =
        List.of(
            new io.qwenbridge.intelligence.ir.QueryEntity("mouse", "PRODUCT", 0, 5),
            new io.qwenbridge.intelligence.ir.QueryEntity("pad", "product", 6, 9));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse pad", entities, List.of())));
  }

  // Stage 40: multibyte evidence can exceed the byte budget without exceeding UTF-16 budget.
  @Test
  void rejectsEntityUtf8EvidenceBudget() {
    String value = "€".repeat(200);
    var entity = new io.qwenbridge.intelligence.ir.QueryEntity(value, "PRODUCT", 0, value.length());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, value, List.of(entity), List.of())));
  }

  // Stage 41: reject competing bounds with identical currency and operator.
  @Test
  void rejectsCompetingPriceUpperBounds() {
    var bounds =
        List.of(
            new LogicalQueryPlan.PriceUpperBound(
                "price", QueryOperator.LESS_THAN, new BigDecimal("100"), "SEK"),
            new LogicalQueryPlan.PriceUpperBound(
                "price", QueryOperator.LESS_THAN, new BigDecimal("200"), "SEK"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(), bounds)));
  }

  // Stage 42: no nonnegative price can satisfy price < 0.
  @Test
  void rejectsUnsatisfiableStrictZeroBound() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StructuredExecutionLimits.validate(plan("mouse", BigDecimal.ZERO)));
  }

  @Test
  void acceptsZeroInclusiveBound() {
    var bound =
        new LogicalQueryPlan.PriceUpperBound(
            "price", QueryOperator.LESS_THAN_OR_EQUAL, BigDecimal.ZERO, "SEK");
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "mouse", List.of(), List.of(bound))));
  }

  // Stage 43: multiline payload limit.
  @Test
  void rejectsExcessiveLines() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "a\n".repeat(33), List.of(), List.of())));
  }

  @Test
  void acceptsCrlfAsOneLine() {
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "a\r\nb", List.of(), List.of())));
  }

  // Stage 44: tab budget.
  @Test
  void rejectsExcessiveTabs() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "a" + "\t".repeat(65), List.of(), List.of())));
  }

  // Stage 45: Unicode combining mark budget.
  @Test
  void rejectsExcessiveCombiningMarks() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "a" + "\u0301".repeat(17), List.of(), List.of())));
  }

  @Test
  void acceptsOrdinaryCombiningAccent() {
    assertDoesNotThrow(
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "cafe\u0301", List.of(), List.of())));
  }

  // Stage 46: private-use characters.
  @Test
  void rejectsPrivateUseUnicode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "x\uE000", List.of(), List.of())));
  }

  // Stage 47: undefined Unicode scalar.
  @Test
  void rejectsUnassignedUnicode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, "x\u0378", List.of(), List.of())));
  }

  // Stage 48: evidence must contain visible text.
  @Test
  void rejectsBlankEntityEvidence() {
    // QueryEntity rejects blank evidence at construction, before runtime validation.
    assertThrows(
        IllegalArgumentException.class,
        () -> new io.qwenbridge.intelligence.ir.QueryEntity("   ", "PRODUCT", 0, 3));
  }

  // Stage 49: serialized decimal values must fit an aggregate budget.
  @Test
  void acceptsOrdinaryPriceTextBudget() {
    assertDoesNotThrow(
        () -> StructuredExecutionLimits.validate(plan("mouse", new BigDecimal("123.45"))));
  }

  // Stage 50: combined constraint budget.
  @Test
  void rejectsExcessCombinedConstraints() {
    String query = "x ".repeat(21);
    var entities = new java.util.ArrayList<io.qwenbridge.intelligence.ir.QueryEntity>();
    for (int i = 0; i < 21; i++) {
      entities.add(new io.qwenbridge.intelligence.ir.QueryEntity("x", "PRODUCT", i * 2, i * 2 + 1));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StructuredExecutionLimits.validate(
                new LogicalQueryPlan(1, query, entities, List.of())));
  }
}
