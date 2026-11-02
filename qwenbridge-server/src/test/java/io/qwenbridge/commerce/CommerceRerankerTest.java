package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.execution.provider.model.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommerceRerankerTest {
  private SearchResultSet input() {
    return new SearchResultSet(
        List.of(
            SearchHit.of("bad", .9, Map.of("title", "mouse")),
            SearchHit.of("good", .2, Map.of("title", "headphones"))),
        2,
        1);
  }

  @Test
  void acceptsOnlyACompletePermutationAndMeasuresUpliftOnAReferenceJudgment() {
    var ai = mock(AIService.class);
    when(ai.chat(any())).thenReturn(new ChatResponse("{\"ids\":[\"good\",\"bad\"]}"));
    var ranker = new CommerceReranker(ai, new ObjectMapper(), 1000);
    try {
      var result = ranker.rerank("headphones", input());
      assertThat(result.method()).isEqualTo("model-permutation-v1");
      var baseline =
          CommerceEvaluation.retrieval(
              List.of(
                  new CommerceEvaluation.Judgment("q", List.of("bad", "good"), Map.of("good", 3))),
              2);
      var after =
          CommerceEvaluation.retrieval(
              List.of(
                  new CommerceEvaluation.Judgment(
                      "q",
                      result.results().hits().stream().map(SearchHit::id).toList(),
                      Map.of("good", 3))),
              2);
      assertThat(after.ndcgAtK()).isGreaterThan(baseline.ndcgAtK());
      when(ai.chat(any())).thenReturn(new ChatResponse("{\"ids\":[\"invented\",\"good\"]}"));
      assertThat(ranker.rerank("headphones", input()).method())
          .isEqualTo("baseline-model-unavailable");
    } finally {
      ranker.close();
    }
  }

  @Test
  void timeoutKeepsTheOriginalResultsAndDoesNotBlockTheCaller() {
    var ai = mock(AIService.class);
    when(ai.chat(any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(5000);
              return new ChatResponse("{}");
            });
    var ranker = new CommerceReranker(ai, new ObjectMapper(), 20);
    try {
      long start = System.nanoTime();
      var original = input();
      var result = ranker.rerank("headphones", original);
      assertThat(result.results()).isSameAs(original);
      assertThat(result.method()).isEqualTo("baseline-model-unavailable");
      assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000);
    } finally {
      ranker.close();
    }
  }

  @Test
  void rerankingPreservesTheRetrievalScoreInsteadOfOverwritingIt() {
    var ai = mock(AIService.class);
    when(ai.chat(any())).thenReturn(new ChatResponse("{\"ids\":[\"good\",\"bad\"]}"));
    var ranker = new CommerceReranker(ai, new ObjectMapper(), 1000);
    try {
      var hits = ranker.rerank("headphones", input()).results().hits();
      assertThat(hits).extracting(SearchHit::id).containsExactly("good", "bad");
      assertThat(hits.getFirst().metadata())
          .containsEntry("retrievalScore", .2)
          .containsEntry("rerankPosition", 1)
          .containsEntry("reranker", "model-permutation-v1");
      assertThat(hits.get(1).metadata()).containsEntry("retrievalScore", .9);
    } finally {
      ranker.close();
    }
  }

  @Test
  void theModelRerankerWorksThroughTheCoreSpiAndItsGuards() {
    var ai = mock(AIService.class);
    when(ai.chat(any())).thenReturn(new ChatResponse("{\"ids\":[\"c\",\"a\",\"b\"]}"));
    var ranker = new CommerceReranker(ai, new ObjectMapper(), 1000);
    try {
      var spi = ranker.asReranker(null);
      assertThat(spi.id()).isEqualTo("model-permutation-v1");
      var policy = new io.qwenbridge.core.ranking.RankingPolicy("p", "1", 1, 0, 0, List.of(), 10);
      java.util.function.Function<String, io.qwenbridge.core.ranking.RankCandidate> c =
          id ->
              new io.qwenbridge.core.ranking.RankCandidate(
                  id,
                  id.charAt(0) - 'a' + 1,
                  new io.qwenbridge.core.confidence.RetrievalScore(
                      1, io.qwenbridge.core.confidence.RetrievalScore.Kind.BM25, "kw"),
                  Map.of(
                      "title", io.qwenbridge.core.retrieval.filter.FieldValue.text("item " + id)));
      var result =
          io.qwenbridge.core.ranking.RankingPipeline.run(
              policy,
              List.of(c.apply("a"), c.apply("b"), c.apply("c")),
              List.of(),
              io.qwenbridge.core.retrieval.filter.Filter.ALL,
              java.util.Optional.of(spi),
              "q",
              java.time.Duration.ofSeconds(2));
      assertThat(result.rerank()).isEqualTo(io.qwenbridge.core.ranking.RerankStage.Status.APPLIED);
      assertThat(result.items())
          .extracting(io.qwenbridge.core.ranking.RankedItem::id)
          .containsExactly("c", "a", "b");
      // An invented id from the model keeps the baseline.
      when(ai.chat(any())).thenReturn(new ChatResponse("{\"ids\":[\"x\",\"a\",\"b\"]}"));
      var invalid =
          io.qwenbridge.core.ranking.RankingPipeline.run(
              policy,
              List.of(c.apply("a"), c.apply("b"), c.apply("c")),
              List.of(),
              io.qwenbridge.core.retrieval.filter.Filter.ALL,
              java.util.Optional.of(spi),
              "q",
              java.time.Duration.ofSeconds(2));
      assertThat(invalid.rerank()).isEqualTo(io.qwenbridge.core.ranking.RerankStage.Status.FAILED);
      assertThat(invalid.items())
          .extracting(io.qwenbridge.core.ranking.RankedItem::id)
          .containsExactly("a", "b", "c");
    } finally {
      ranker.close();
    }
  }
}
