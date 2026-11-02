package io.qwenbridge.ranking.policy;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.ranking.BaselineRanker;
import io.qwenbridge.core.ranking.RankCandidate;
import io.qwenbridge.core.ranking.RankedItem;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** QB-117, QB-121: the configured policy is versioned, applied and fails closed. */
class RankingPoliciesTest {
  static RankingPolicies policies(double businessWeight, String rules) {
    return new RankingPolicies(
        "shop-ranking", "2.0", 1.0, 0.5, businessWeight, 10, Optional.ofNullable(rules));
  }

  @Test
  void configuredRulesReorderAndTheVersionIsReported() {
    var policy =
        policies(
                0.8,
                "[{\"id\":\"promote-stock\",\"field\":\"inStock\",\"value\":true,\"boost\":1},"
                    + "{\"id\":\"demote-brand\",\"field\":\"brand\",\"value\":\"Acme\",\"boost\":-0.5}]")
            .policy();
    assertThat(policy.key()).isEqualTo("shop-ranking@2.0");
    assertThat(policy.rules())
        .extracting(r -> r.id())
        .containsExactly("promote-stock", "demote-brand");
    var score = new RetrievalScore(1, RetrievalScore.Kind.BM25, "kw");
    var ranked =
        BaselineRanker.rank(
            policy,
            List.of(
                new RankCandidate("first", 1, score, Map.of("inStock", new FieldValue.Bool(false))),
                new RankCandidate(
                    "second",
                    2,
                    score,
                    Map.of(
                        "inStock", new FieldValue.Bool(true), "brand", FieldValue.text("Nike")))),
            List.of());
    assertThat(ranked).extracting(RankedItem::id).containsExactly("second", "first");
  }

  @Test
  void invalidConfigurationStopsStartup() {
    assertThatThrownBy(() -> policies(0.5, "not json")).hasMessageContaining("JSON");
    assertThatThrownBy(() -> policies(0.5, "{}")).hasMessageContaining("array");
    assertThatThrownBy(
            () -> policies(0.5, "[{\"id\":\"r\",\"field\":\"a b\",\"value\":1,\"boost\":1}]"))
        .hasMessageContaining("field");
    assertThatThrownBy(
            () -> policies(0.5, "[{\"id\":\"r\",\"field\":\"a\",\"value\":{},\"boost\":1}]"))
        .hasMessageContaining("value");
    assertThatThrownBy(
            () -> policies(0.5, "[{\"id\":\"r\",\"field\":\"a\",\"value\":1,\"boost\":3}]"))
        .hasMessageContaining("boost");
    assertThatThrownBy(
            () -> policies(0.0, "[{\"id\":\"r\",\"field\":\"a\",\"value\":1,\"boost\":1}]"))
        .hasMessageContaining("business weight");
    assertThatThrownBy(() -> policies(2.0, null)).hasMessageContaining("outweigh");
    assertThat(policies(0, null).policy().rules()).isEmpty();
  }
}
