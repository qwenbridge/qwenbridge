package io.qwenbridge.ranking.service;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.ranking.model.RankingScore;
import io.qwenbridge.ranking.policy.RankingPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class SearchResultRanker {

  private static final String RANKING_SCORE_METADATA_KEY = "rankingScore";
  private static final String FINAL_SCORE_METADATA_KEY = "finalScore";

  private final RankingPolicy rankingPolicy;

  public SearchResultSet rank(SearchResultSet resultSet) {
    if (resultSet == null || resultSet.isEmpty()) {
      return SearchResultSet.empty();
    }

    double lexicalScale = scale(resultSet, "lexicalScore", true);
    double vectorScale = scale(resultSet, "vectorScore", false);
    var rankedHits =
        resultSet.hits().stream()
            .map(hit -> normalizeRetrievalScores(hit, lexicalScale, vectorScale))
            .map(this::rank)
            .sorted(
                Comparator.comparingDouble(SearchHit::score)
                    .reversed()
                    .thenComparing(SearchHit::id))
            .toList();

    return new SearchResultSet(rankedHits, resultSet.totalHits(), resultSet.tookMillis());
  }

  private SearchHit rank(SearchHit hit) {
    RankingScore rankingScore = rankingPolicy.score(hit);

    Map<String, Object> metadata = new LinkedHashMap<>(hit.metadata());
    metadata.put(RANKING_SCORE_METADATA_KEY, rankingScore);
    metadata.put(FINAL_SCORE_METADATA_KEY, rankingScore.finalScore());
    metadata.put("rankingPolicy", io.qwenbridge.ranking.policy.DefaultRankingPolicy.ID);

    return new SearchHit(hit.id(), rankingScore.finalScore(), hit.document(), metadata);
  }

  private double scale(SearchResultSet results, String key, boolean useHitScore) {
    return results.hits().stream()
        .mapToDouble(hit -> rawScore(hit, key, useHitScore))
        .filter(Double::isFinite)
        .filter(score -> score > 1.0)
        .max()
        .orElse(1.0);
  }

  private double rawScore(SearchHit hit, String key, boolean useHitScore) {
    Object value = hit.metadata().get(key);
    return value instanceof Number number ? number.doubleValue() : useHitScore ? hit.score() : 0.0;
  }

  private SearchHit normalizeRetrievalScores(
      SearchHit hit, double lexicalScale, double vectorScale) {
    Map<String, Object> metadata = new LinkedHashMap<>(hit.metadata());
    metadata.put("originalScore", hit.score());
    metadata.put("lexicalScore", rawScore(hit, "lexicalScore", true) / lexicalScale);
    metadata.put("vectorScore", rawScore(hit, "vectorScore", false) / vectorScale);
    return new SearchHit(hit.id(), hit.score(), hit.document(), metadata);
  }
}
