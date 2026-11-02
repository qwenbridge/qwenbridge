package io.qwenbridge.intelligence.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.semantic.SemanticAmbiguity;
import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticEntity;
import io.qwenbridge.semantic.SemanticEntityType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryEntityConstraintExtractorTest {
  private final QueryEntityConstraintExtractor extractor = new QueryEntityConstraintExtractor();

  private SemanticAnalysis analysis(String query, SemanticEntity... entities) {
    return new SemanticAnalysis(
        query,
        query.toLowerCase(),
        query,
        List.of(entities),
        List.of(),
        SemanticAmbiguity.none(),
        0.9);
  }

  @Test
  void extractsOnlySourceGroundedEntities() {
    var result =
        extractor.extract(
            analysis(
                "Buy Apple phone",
                new SemanticEntity("Apple", SemanticEntityType.BRAND, 0.9),
                new SemanticEntity("Samsung", SemanticEntityType.BRAND, 0.9)));
    assertThat(result.entities()).hasSize(1);
    assertThat(result.entities().getFirst().value()).isEqualTo("Apple");
    assertThat(result.entities().getFirst().start()).isEqualTo(4);
  }

  @Test
  void rejectsSubstringEvidence() {
    var result =
        extractor.extract(
            analysis("pineapple", new SemanticEntity("apple", SemanticEntityType.BRAND, 0.9)));
    assertThat(result.entities()).isEmpty();
  }

  @Test
  void extractsExplicitPriceBoundWithoutCurrencyGuessing() {
    var result = extractor.extract(analysis("laptop under 1200 SEK"));
    assertThat(result.priceConstraints()).hasSize(1);
    var bound = result.priceConstraints().getFirst();
    assertThat(bound.amount()).isEqualByComparingTo(new BigDecimal("1200"));
    assertThat(bound.currency()).isEqualTo("SEK");
    assertThat(bound.operator()).isEqualTo(ExtractedQuery.Operator.LESS_THAN);
  }

  @Test
  void respectsInclusiveUpperBound() {
    var result = extractor.extract(analysis("laptop up to EUR 999.95"));
    assertThat(result.priceConstraints()).hasSize(1);
    assertThat(result.priceConstraints().getFirst().operator())
        .isEqualTo(ExtractedQuery.Operator.LESS_THAN_OR_EQUAL);
  }

  @Test
  void skipsAmbiguousCurrency() {
    assertThat(extractor.extract(analysis("under 1000")).priceConstraints()).isEmpty();
    assertThat(extractor.extract(analysis("under USD 100 EUR")).priceConstraints()).isEmpty();
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> extractor.extract(null)).isInstanceOf(NullPointerException.class);
  }
}
