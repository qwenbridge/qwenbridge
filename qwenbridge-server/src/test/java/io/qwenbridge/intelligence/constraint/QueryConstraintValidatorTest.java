package io.qwenbridge.intelligence.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.schema.QuerySchemaRegistry;
import io.qwenbridge.intelligence.schema.QuerySchemaValidator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryConstraintValidatorTest {
  private final QueryConstraintValidator validator =
      new QueryConstraintValidator(new QuerySchemaValidator(new QuerySchemaRegistry()));

  private static QueryIR query(String text, QueryConstraint... constraints) {
    return new QueryIR(QueryIR.CURRENT_VERSION, text, List.of(), List.of(constraints));
  }

  private static QueryConstraint price(QueryOperator operator, String amount, String currency) {
    return new QueryConstraint("price", operator, new BigDecimal(amount), currency, 0, 5);
  }

  @Test
  void acceptsValidConstraint() {
    assertThat(
            validator
                .validate(query("under 100 SEK", price(QueryOperator.LESS_THAN, "100", "SEK")))
                .valid())
        .isTrue();
  }

  @Test
  void rejectsDuplicateEvenWithDifferentDecimalScale() {
    var result =
        validator.validate(
            query(
                "under 100 SEK under 100.00 SEK",
                price(QueryOperator.LESS_THAN, "100", "SEK"),
                price(QueryOperator.LESS_THAN, "100.00", "SEK")));
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(error -> error.contains("duplicate"));
  }

  @Test
  void allowsDistinctCompatibleUpperBounds() {
    assertThat(
            validator
                .validate(
                    query(
                        "under 100 SEK below 80 SEK",
                        price(QueryOperator.LESS_THAN, "100", "SEK"),
                        price(QueryOperator.LESS_THAN, "80", "SEK")))
                .valid())
        .isTrue();
  }

  @Test
  void doesNotTreatDifferentCurrenciesAsDuplicates() {
    assertThat(
            validator
                .validate(
                    query(
                        "under 100 SEK under 100 EUR",
                        price(QueryOperator.LESS_THAN, "100", "SEK"),
                        price(QueryOperator.LESS_THAN, "100", "EUR")))
                .valid())
        .isTrue();
  }

  @Test
  void propagatesSchemaErrors() {
    var result =
        validator.validate(query("under 100 GBP", price(QueryOperator.LESS_THAN, "100", "GBP")));
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(error -> error.contains("currency"));
  }

  @Test
  void returnsImmutableErrors() {
    var result =
        validator.validate(query("under 100 GBP", price(QueryOperator.LESS_THAN, "100", "GBP")));
    assertThat(result.errors()).isUnmodifiable();
  }
}
