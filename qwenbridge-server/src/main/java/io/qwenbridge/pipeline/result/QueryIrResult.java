package io.qwenbridge.pipeline.result;

import io.qwenbridge.core.queryir.IrViolation;
import io.qwenbridge.core.queryir.QueryIR;
import java.util.List;
import java.util.Optional;

/** The assembled QueryIR, or the reasons it could not be assembled validly. */
public record QueryIrResult(Optional<QueryIR> ir, List<IrViolation> violations) {

  public QueryIrResult {
    ir = ir == null ? Optional.empty() : ir;
    violations = violations == null ? List.of() : List.copyOf(violations);
  }

  public static QueryIrResult of(QueryIR ir) {
    return new QueryIrResult(Optional.of(ir), List.of());
  }

  public static QueryIrResult invalid(List<IrViolation> violations) {
    return new QueryIrResult(Optional.empty(), violations);
  }
}
