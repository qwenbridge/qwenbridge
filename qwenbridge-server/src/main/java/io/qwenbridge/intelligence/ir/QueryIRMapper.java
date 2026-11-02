package io.qwenbridge.intelligence.ir;

import io.qwenbridge.intelligence.extraction.ExtractedQuery;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;

/** Lossless mapping of Stage 5 extraction results to a typed IR. */
@ApplicationScoped
public class QueryIRMapper {
  public QueryIR map(String originalQuery, ExtractedQuery extracted) {
    Objects.requireNonNull(extracted, "extracted");
    List<QueryEntity> entities =
        extracted.entities().stream()
            .map(e -> new QueryEntity(e.value(), e.type(), e.start(), e.end()))
            .toList();
    List<QueryConstraint> constraints =
        extracted.priceConstraints().stream()
            .map(
                c ->
                    new QueryConstraint(
                        "price",
                        QueryOperator.valueOf(c.operator().name()),
                        c.amount(),
                        c.currency(),
                        c.start(),
                        c.end()))
            .toList();
    return new QueryIR(QueryIR.CURRENT_VERSION, originalQuery, entities, constraints);
  }
}
