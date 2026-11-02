package io.qwenbridge.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-207, QB-209: the multidomain evaluation set on the Core BM25 index, offline and reproducible.
 * Writes target/evaluation/keyword-core.json. The vector and hybrid runs need the embedding model
 * and live in the server's RetrievalQualityEvaluationTest.
 */
class KeywordQualityBaselineTest {

  static final Path DATASET = Path.of("..", "evaluation", "datasets", "multidomain-1.0.json");

  static QualityEvaluation.Report run() throws Exception {
    var dataset = QualityEvaluation.Dataset.read(DATASET);
    var index = new InMemoryKeywordIndex("eval", 1_000, 100);
    for (var d : dataset.documents())
      index.put(new IndexedDocument("eval", d.id(), d.title() + " " + d.text(), Map.of()));
    return QualityEvaluation.evaluate(
        dataset,
        "keyword-bm25-core",
        q -> {
          var outcome =
              index.search(
                  SearchRequest.keyword("eval", q.text(), Filter.ALL, 5),
                  CancellationToken.create());
          List<String> ids =
              outcome instanceof SearchOutcome.Success s
                  ? s.hits().stream().map(ScoredDocument::id).toList()
                  : List.of();
          return new QualityEvaluation.Outcome(
              ids, !(outcome instanceof SearchOutcome.Success), outcome.getClass().getSimpleName());
        },
        5,
        "heldout");
  }

  @Test
  void keywordQualityIsMeasuredReproduciblyAndWritten() throws Exception {
    var first = run();
    var second = run();
    assertEquals(first, second, "the same data and code give the same numbers");
    assertEquals(27, first.overall().queries());
    assertEquals(
        24, first.overall().scored(), "the three held-out negative queries are scored separately");
    assertTrue(first.byLanguage().keySet().containsAll(List.of("en", "fa", "sv", "mixed")));
    Files.createDirectories(Path.of("target", "evaluation"));
    new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValue(Path.of("target", "evaluation", "keyword-core.json").toFile(), first);
    var o = first.overall();
    System.out.printf(
        "keyword-core heldout k=5: P@5=%.3f R@5=%.3f MRR=%.3f nDCG@5=%.3f coverage=%.3f"
            + " negativeFP=%s%n",
        o.precisionAtK(),
        o.recallAtK(),
        o.mrr(),
        o.ndcgAtK(),
        o.coverage(),
        o.negativeFalsePositiveRate());
  }
}
