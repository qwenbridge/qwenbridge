package io.qwenbridge.intelligence.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.intelligence.ir.QueryOperator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalExecutionPlannerTest {
  private final LogicalExecutionPlanner planner =
      new LogicalExecutionPlanner(new ExecutionPlanFactory());

  private static LogicalQueryPlan unfiltered() {
    return new LogicalQueryPlan(1, "laptops", List.of(), List.of());
  }

  @Test
  void preservesKeywordDecision() {
    var result = planner.plan(unfiltered(), SearchDecision.keyword());
    assertThat(result.contains(ExecutionOperation.KEYWORD_SEARCH)).isTrue();
    assertThat(result.contains(ExecutionOperation.RETURN_RESULTS)).isTrue();
  }

  @Test
  void preservesHybridDecision() {
    var result = planner.plan(unfiltered(), SearchDecision.hybrid());
    assertThat(result.contains(ExecutionOperation.HYBRID_SEARCH)).isTrue();
    assertThat(result.contains(ExecutionOperation.RERANK_RESULTS)).isTrue();
  }

  @Test
  void preservesDirectAnswer() {
    var result = planner.plan(unfiltered(), SearchDecision.directAnswer());
    assertThat(result.contains(ExecutionOperation.DIRECT_ANSWER)).isTrue();
  }

  @Test
  void rejectsUnsupportedPriceFilterInsteadOfSilentlyIgnoringIt() {
    var filtered =
        new LogicalQueryPlan(
            1,
            "laptops under 100 SEK",
            List.of(),
            List.of(
                new LogicalQueryPlan.PriceUpperBound(
                    "price", QueryOperator.LESS_THAN, new BigDecimal("100"), "SEK")));
    assertThatThrownBy(() -> planner.plan(filtered, SearchDecision.keyword()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("price filters");
  }

  @Test
  void rejectsSearchWithoutBackend() {
    var invalid =
        new SearchDecision(
            SearchMode.KEYWORD,
            SearchBackend.NONE,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            0.7,
            "invalid backend");
    assertThatThrownBy(() -> planner.plan(unfiltered(), invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("search backend");
  }

  @Test
  void rejectsNullInputs() {
    assertThatThrownBy(() -> planner.plan(null, SearchDecision.keyword()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> planner.plan(unfiltered(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
