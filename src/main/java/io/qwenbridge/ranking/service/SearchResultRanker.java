package io.qwenbridge.ranking.service;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.ranking.model.RankingScore;
import io.qwenbridge.ranking.policy.RankingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchResultRanker {

    private static final String RANKING_SCORE_METADATA_KEY = "rankingScore";
    private static final String FINAL_SCORE_METADATA_KEY = "finalScore";

    private final RankingPolicy rankingPolicy;

    public SearchResultSet rank(SearchResultSet resultSet) {
        if (resultSet == null || resultSet.isEmpty()) {
            return SearchResultSet.empty();
        }

        var rankedHits = resultSet.hits()
                .stream()
                .map(this::rank)
                .sorted(Comparator
                        .comparingDouble(SearchHit::score)
                        .reversed()
                        .thenComparing(SearchHit::id))
                .toList();

        return new SearchResultSet(
                rankedHits,
                resultSet.totalHits(),
                resultSet.tookMillis()
        );
    }

    private SearchHit rank(SearchHit hit) {
        RankingScore rankingScore = rankingPolicy.score(hit);

        Map<String, Object> metadata = new LinkedHashMap<>(hit.metadata());
        metadata.put(RANKING_SCORE_METADATA_KEY, rankingScore);
        metadata.put(FINAL_SCORE_METADATA_KEY, rankingScore.finalScore());

        return new SearchHit(
                hit.id(),
                rankingScore.finalScore(),
                hit.document(),
                metadata
        );
    }
}
