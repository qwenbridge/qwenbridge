package io.qwenbridge.core.ranking;

import io.qwenbridge.core.retrieval.filter.Filter;
import java.util.Objects;

/**
 * A business preference applied at ranking time (QB-121), such as promoting in-stock items or
 * demoting a clearance brand. It can only move results that already passed authorization, the
 * tenant boundary and every HARD constraint: it never adds, removes or reveals a result, and the
 * final re-validation removes anything that violates a HARD constraint whatever its boost.
 *
 * @param boost in [−1, 1], non-zero; positive promotes, negative demotes
 */
public record BusinessRule(String id, Filter condition, double boost) {
  public BusinessRule {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(condition, "condition must not be null");
    if (id.isBlank() || id.length() > 64) {
      throw new IllegalArgumentException("rule id must have 1..64 characters");
    }
    if (!Double.isFinite(boost) || boost == 0 || boost < -1 || boost > 1) {
      throw new IllegalArgumentException("boost must be non-zero and within [-1, 1]");
    }
  }
}
