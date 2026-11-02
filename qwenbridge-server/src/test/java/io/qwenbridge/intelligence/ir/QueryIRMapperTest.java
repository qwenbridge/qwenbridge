package io.qwenbridge.intelligence.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.intelligence.extraction.ExtractedQuery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryIRMapperTest {
  private final QueryIRMapper mapper = new QueryIRMapper();

  @Test
  void mapsGroundedEntitiesAndPriceConstraintsWithoutLosingPrecision() {
    String query = "Apple laptop under 1200.50 SEK";
    ExtractedQuery extracted =
        new ExtractedQuery(
            List.of(new ExtractedQuery.GroundedEntity("Apple", "BRAND", 0, 5)),
            List.of(
                new ExtractedQuery.PriceConstraint(
                    ExtractedQuery.Operator.LESS_THAN,
                    new BigDecimal("1200.50"),
                    "SEK",
                    13,
                    query.length())));
    QueryIR ir = mapper.map(query, extracted);
    assertThat(ir.version()).isEqualTo(1);
    assertThat(ir.entities()).containsExactly(new QueryEntity("Apple", "BRAND", 0, 5));
    assertThat(ir.constraints())
        .containsExactly(
            new QueryConstraint(
                "price",
                QueryOperator.LESS_THAN,
                new BigDecimal("1200.50"),
                "SEK",
                13,
                query.length()));
  }

  @Test
  void acceptsEmptyExtraction() {
    QueryIR ir = mapper.map("laptop", new ExtractedQuery(List.of(), List.of()));
    assertThat(ir.entities()).isEmpty();
    assertThat(ir.constraints()).isEmpty();
  }

  @Test
  void rejectsUntrustedEntityEvidence() {
    ExtractedQuery extracted =
        new ExtractedQuery(
            List.of(new ExtractedQuery.GroundedEntity("Samsung", "BRAND", 0, 7)), List.of());
    assertThatThrownBy(() -> mapper.map("Apple laptop", extracted))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOutOfRangeConstraintOffsets() {
    ExtractedQuery extracted =
        new ExtractedQuery(
            List.of(),
            List.of(
                new ExtractedQuery.PriceConstraint(
                    ExtractedQuery.Operator.LESS_THAN_OR_EQUAL, BigDecimal.TEN, "EUR", 0, 100)));
    assertThatThrownBy(() -> mapper.map("under 10 EUR", extracted))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnsupportedVersionAndNullInput() {
    assertThatThrownBy(() -> new QueryIR(2, "laptop", List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map("laptop", null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void copiesListsDefensively() {
    java.util.ArrayList<QueryEntity> entities = new java.util.ArrayList<>();
    QueryIR ir = new QueryIR(1, "laptop", entities, List.of());
    entities.add(new QueryEntity("laptop", "PRODUCT", 0, 6));
    assertThat(ir.entities()).isEmpty();
    assertThatThrownBy(() -> ir.entities().add(new QueryEntity("laptop", "PRODUCT", 0, 6)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
