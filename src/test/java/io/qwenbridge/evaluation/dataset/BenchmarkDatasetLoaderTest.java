package io.qwenbridge.evaluation.dataset;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkDatasetLoaderTest {

    private final BenchmarkDatasetLoader loader = new BenchmarkDatasetLoader();

    @Test
    void shouldLoadBenchmarkCsvIntoEvaluationQueries() {
        String csv = """
                queryId,query,documentId,relevance
                q1,gaming mouse,doc-mouse-1,3
                q1,gaming mouse,doc-mouse-2,2
                q2,standing desk,doc-desk-1,3
                """;

        List<EvaluationQuery> queries = loader.load(new StringReader(csv));

        assertThat(queries).hasSize(2);

        assertThat(queries.getFirst().id()).isEqualTo("q1");
        assertThat(queries.getFirst().query()).isEqualTo("gaming mouse");
        assertThat(queries.getFirst().labels()).hasSize(2);
        assertThat(queries.getFirst().labels().getFirst().documentId())
                .isEqualTo("doc-mouse-1");
        assertThat(queries.getFirst().labels().getFirst().relevance())
                .isEqualTo(3);

        assertThat(queries.get(1).id()).isEqualTo("q2");
        assertThat(queries.get(1).labels()).hasSize(1);
    }

    @Test
    void shouldSkipBlankLines() {
        String csv = """
                queryId,query,documentId,relevance

                q1,gaming mouse,doc-mouse-1,3

                """;

        List<EvaluationQuery> queries = loader.load(new StringReader(csv));

        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().labels()).hasSize(1);
    }

    @Test
    void shouldRejectMalformedRows() {
        String csv = """
                queryId,query,documentId,relevance
                q1,gaming mouse,doc-mouse-1
                """;

        assertThatThrownBy(() -> loader.load(new StringReader(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("benchmark row must contain exactly 4 columns");
    }
}
