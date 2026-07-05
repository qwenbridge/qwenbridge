package io.qwenbridge.ranking.policy;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.ranking.model.RankingScore;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DefaultRankingPolicy implements RankingPolicy {

  private static final double LEXICAL_WEIGHT = 0.45;
  private static final double VECTOR_WEIGHT = 0.45;
  private static final double METADATA_WEIGHT = 0.07;
  private static final double FRESHNESS_WEIGHT = 0.03;

  @Override
  public RankingScore score(SearchHit hit) {
    double lexicalScore = normalize(scoreFromMetadata(hit.metadata(), "lexicalScore", hit.score()));
    double vectorScore = normalize(scoreFromMetadata(hit.metadata(), "vectorScore", 0.0));
    double metadataBoost = normalize(scoreFromMetadata(hit.metadata(), "metadataBoost", 0.0));
    double freshnessBoost = normalize(scoreFromMetadata(hit.metadata(), "freshnessBoost", 0.0));

    double finalScore =
        normalize(
            lexicalScore * LEXICAL_WEIGHT
                + vectorScore * VECTOR_WEIGHT
                + metadataBoost * METADATA_WEIGHT
                + freshnessBoost * FRESHNESS_WEIGHT);

    return new RankingScore(lexicalScore, vectorScore, metadataBoost, freshnessBoost, finalScore);
  }

  private double scoreFromMetadata(Map<String, Object> metadata, String key, double fallback) {
    Object value = metadata.get(key);

    if (value instanceof Number number) {
      return number.doubleValue();
    }

    return fallback;
  }

  private double normalize(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }

    if (value < 0.0) {
      return 0.0;
    }

    if (value > 1.0) {
      return 1.0;
    }

    return value;
  }
}
