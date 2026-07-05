package io.qwenbridge.ranking.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.ranking.model.RankingScore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultRankingPolicyTest {

  private final DefaultRankingPolicy policy = new DefaultRankingPolicy();

  @Test
  void shouldCombineLexicalVectorMetadataAndFreshnessScores() {
    SearchHit hit =
        new SearchHit(
            "doc-1",
            0.8,
            Map.of("title", "Gaming mouse"),
            Map.of(
                "lexicalScore", 0.8,
                "vectorScore", 0.6,
                "metadataBoost", 0.5,
                "freshnessBoost", 0.2));

    RankingScore score = policy.score(hit);

    assertThat(score.lexicalScore()).isEqualTo(0.8);
    assertThat(score.vectorScore()).isEqualTo(0.6);
    assertThat(score.metadataBoost()).isEqualTo(0.5);
    assertThat(score.freshnessBoost()).isEqualTo(0.2);
    assertThat(score.finalScore()).isCloseTo(0.671, org.assertj.core.data.Offset.offset(0.000001));
  }

  @Test
  void shouldFallbackToSearchHitScoreAsLexicalScore() {
    SearchHit hit = SearchHit.of("doc-1", 0.7, Map.of("title", "Desk"));

    RankingScore score = policy.score(hit);

    assertThat(score.lexicalScore()).isEqualTo(0.7);
    assertThat(score.vectorScore()).isZero();
    assertThat(score.finalScore()).isEqualTo(0.315);
  }

  @Test
  void shouldClampScoresIntoNormalizedRange() {
    SearchHit hit =
        new SearchHit(
            "doc-1",
            2.0,
            Map.of("title", "Desk"),
            Map.of(
                "lexicalScore",
                2.0,
                "vectorScore",
                -1.0,
                "metadataBoost",
                Double.NaN,
                "freshnessBoost",
                Double.POSITIVE_INFINITY));

    RankingScore score = policy.score(hit);

    assertThat(score.lexicalScore()).isEqualTo(1.0);
    assertThat(score.vectorScore()).isZero();
    assertThat(score.metadataBoost()).isZero();
    assertThat(score.freshnessBoost()).isZero();
    assertThat(score.finalScore()).isEqualTo(0.45);
  }
}
