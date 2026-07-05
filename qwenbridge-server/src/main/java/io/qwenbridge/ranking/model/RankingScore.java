package io.qwenbridge.ranking.model;

public record RankingScore(
    double lexicalScore,
    double vectorScore,
    double metadataBoost,
    double freshnessBoost,
    double finalScore) {

  public RankingScore {
    lexicalScore = normalize(lexicalScore);
    vectorScore = normalize(vectorScore);
    metadataBoost = normalize(metadataBoost);
    freshnessBoost = normalize(freshnessBoost);
    finalScore = normalize(finalScore);
  }

  public static RankingScore zero() {
    return new RankingScore(0.0, 0.0, 0.0, 0.0, 0.0);
  }

  private static double normalize(double value) {
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
