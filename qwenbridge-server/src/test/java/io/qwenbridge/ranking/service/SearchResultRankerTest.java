package io.qwenbridge.ranking.service;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.ranking.model.RankingScore;
import io.qwenbridge.ranking.policy.DefaultRankingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultRankerTest {

    private final SearchResultRanker ranker =
            new SearchResultRanker(new DefaultRankingPolicy());

    @Test
    void shouldAttachRankingScoreAndSortByFinalScoreDescending() {
        SearchHit first = new SearchHit(
                "doc-1",
                0.2,
                Map.of("title", "Desk"),
                Map.of(
                        "lexicalScore", 0.2,
                        "vectorScore", 0.2
                )
        );

        SearchHit second = new SearchHit(
                "doc-2",
                0.9,
                Map.of("title", "Gaming desk"),
                Map.of(
                        "lexicalScore", 0.9,
                        "vectorScore", 0.9
                )
        );

        SearchResultSet ranked = ranker.rank(
                new SearchResultSet(List.of(first, second), 2, 12)
        );

        assertThat(ranked.totalHits()).isEqualTo(2);
        assertThat(ranked.tookMillis()).isEqualTo(12);
        assertThat(ranked.hits()).extracting(SearchHit::id)
                .containsExactly("doc-2", "doc-1");

        SearchHit top = ranked.hits().getFirst();

        assertThat(top.metadata()).containsKey("rankingScore");
        assertThat(top.metadata()).containsKey("finalScore");
        assertThat(top.metadata().get("rankingScore"))
                .isInstanceOf(RankingScore.class);
        assertThat(top.score()).isEqualTo(top.metadata().get("finalScore"));
    }

    @Test
    void shouldPreserveStableOrderByIdWhenFinalScoresTie() {
        SearchHit b = SearchHit.of("doc-b", 0.5, Map.of("title", "B"));
        SearchHit a = SearchHit.of("doc-a", 0.5, Map.of("title", "A"));

        SearchResultSet ranked = ranker.rank(
                new SearchResultSet(List.of(b, a), 2, 1)
        );

        assertThat(ranked.hits()).extracting(SearchHit::id)
                .containsExactly("doc-a", "doc-b");
    }

    @Test
    void shouldReturnEmptyResultSetForNullOrEmptyInput() {
        assertThat(ranker.rank(null).isEmpty()).isTrue();
        assertThat(ranker.rank(SearchResultSet.empty()).isEmpty()).isTrue();
    }
}
