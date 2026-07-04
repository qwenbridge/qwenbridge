package io.qwenbridge.reranking;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRerankerTest {

    @Test
    void shouldReturnOriginalResultSetWithoutChangingOrder() {
        SearchResultSet resultSet = new SearchResultSet(
                List.of(
                        SearchHit.of("doc-1", 0.8, Map.of("title", "First")),
                        SearchHit.of("doc-2", 0.4, Map.of("title", "Second"))
                ),
                2,
                5
        );

        SearchResultSet reranked =
                new NoOpReranker().rerank("wireless mouse", resultSet);

        assertThat(reranked).isSameAs(resultSet);
        assertThat(reranked.hits()).extracting(SearchHit::id)
                .containsExactly("doc-1", "doc-2");
    }
}
