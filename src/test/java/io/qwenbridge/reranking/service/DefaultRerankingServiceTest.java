package io.qwenbridge.reranking.service;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.reranking.Reranker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultRerankingServiceTest {

    private final SearchResultSet source = new SearchResultSet(
            List.of(
                    SearchHit.of("doc-1", 0.4, Map.of("title", "First")),
                    SearchHit.of("doc-2", 0.8, Map.of("title", "Second"))
            ),
            2,
            7
    );

    @Test
    void shouldReturnRerankedResultsWhenRerankerSucceeds() {
        Reranker reranker = mock(Reranker.class);

        SearchResultSet reranked = new SearchResultSet(
                List.of(
                        SearchHit.of("doc-2", 0.95, Map.of("title", "Second")),
                        SearchHit.of("doc-1", 0.55, Map.of("title", "First"))
                ),
                2,
                7
        );

        when(reranker.rerank("wireless mouse", source)).thenReturn(reranked);

        DefaultRerankingService service =
                new DefaultRerankingService(reranker, Duration.ofSeconds(1));

        assertThat(service.rerank("wireless mouse", source)).isSameAs(reranked);
    }

    @Test
    void shouldReturnOriginalResultsWhenRerankerFails() {
        Reranker reranker = mock(Reranker.class);
        when(reranker.rerank("wireless mouse", source))
                .thenThrow(new RuntimeException("reranker unavailable"));

        DefaultRerankingService service =
                new DefaultRerankingService(reranker, Duration.ofSeconds(1));

        assertThat(service.rerank("wireless mouse", source)).isSameAs(source);
    }

    @Test
    void shouldReturnOriginalResultsWhenRerankerTimesOut() {
        Reranker reranker = (query, results) -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return results;
        };

        DefaultRerankingService service =
                new DefaultRerankingService(reranker, Duration.ofMillis(20));

        assertThat(service.rerank("wireless mouse", source)).isSameAs(source);
    }

    @Test
    void shouldSkipRerankerForEmptyResults() {
        Reranker reranker = mock(Reranker.class);

        DefaultRerankingService service =
                new DefaultRerankingService(reranker, Duration.ofSeconds(1));

        SearchResultSet empty = SearchResultSet.empty();

        assertThat(service.rerank("wireless mouse", empty)).isSameAs(empty);
        verifyNoInteractions(reranker);
    }
}
