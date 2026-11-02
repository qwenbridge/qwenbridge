package io.qwenbridge.reranking;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryTermRerankerTest {
  private final QueryTermReranker reranker = new QueryTermReranker();

  @Test
  void queryEvidenceCanPromoteALowerScoredRelevantDocument() {
    var source =
        new SearchResultSet(
            List.of(
                SearchHit.of("chair", .9, Map.of("title", "Office chair")),
                SearchHit.of("mouse", .1, Map.of("title", "Wireless gaming mouse"))),
            12,
            7);
    var result = reranker.rerank("gaming mouse", source);
    assertThat(result.hits()).extracting(SearchHit::id).containsExactly("mouse", "chair");
    assertThat(result.hits().getFirst().metadata()).containsEntry("queryTermCoverage", 1.0);
    assertThat(result.totalHits()).isEqualTo(12);
    assertThat(result.tookMillis()).isEqualTo(7);
    assertThat(source.hits().getFirst().metadata()).isEmpty();
  }

  @Test
  void missingTermEvidenceMustPreserveTheOriginalResult() {
    var source = new SearchResultSet(List.of(SearchHit.of("x", .8, Map.of("title", "Desk"))), 1, 2);
    assertThat(reranker.rerank("headphones", source)).isSameAs(source);
    assertThat(reranker.rerank("!!!", source)).isSameAs(source);
  }

  @Test
  void unicodeTermsAreMatchedWithoutSubstringFalsePositives() {
    var source =
        new SearchResultSet(
            List.of(
                SearchHit.of("bad", .9, Map.of("title", "میزبان")),
                SearchHit.of("good", .1, Map.of("title", "میز ارزان"))),
            2,
            1);
    assertThat(reranker.rerank("میز", source).hits())
        .extracting(SearchHit::id)
        .containsExactly("good", "bad");
  }

  @org.junit.jupiter.api.Test
  void theSameBaselineIsAvailableOnTheCoreSpi() {
    var spi = (io.qwenbridge.core.ranking.Reranker) new QueryTermReranker();
    var items =
        java.util.List.of(
            new io.qwenbridge.core.ranking.Reranker.Item(
                "mouse",
                java.util.Map.of(
                    "title", io.qwenbridge.core.retrieval.filter.FieldValue.text("Gaming mouse"))),
            new io.qwenbridge.core.ranking.Reranker.Item(
                "phones",
                java.util.Map.of(
                    "title",
                    io.qwenbridge.core.retrieval.filter.FieldValue.text("Wireless headphones"))),
            new io.qwenbridge.core.ranking.Reranker.Item("empty", java.util.Map.of()));
    var response = spi.rerank("wireless headphones", items);
    org.assertj.core.api.Assertions.assertThat(response.order())
        .containsExactly("phones", "mouse", "empty");
    org.assertj.core.api.Assertions.assertThat(response.scores().orElseThrow())
        .containsExactly(1.0, 0.0, 0.0);
    org.assertj.core.api.Assertions.assertThat(spi.id()).isEqualTo("query-terms-v1");
  }
}
