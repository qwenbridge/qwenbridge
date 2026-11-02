package io.qwenbridge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.evaluation.QualityEvaluation;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.retrieval.hybrid.HybridRetriever;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-210: keyword, vector and hybrid retrieval measured on the same held-out queries of the
 * multidomain dataset, with the configured embedding model (live; enabled by
 * QWENBRIDGE_RUN_OLLAMA_IT). Retrieval is the Core implementation the platform uses: BM25, cosine
 * kNN and reciprocal-rank fusion (k=60). Reports go to target/evaluation/quality-*.json and a
 * comparison table to target/evaluation/quality-comparison.md; no thresholds are asserted here
 * (regression gates use recorded baselines, QB-224).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class RetrievalQualityEvaluationTest {

  static final Path DATASET = Path.of("..", "evaluation", "datasets", "multidomain-1.0.json");
  static final int K = 5;

  @Inject EmbeddingStages embeddings;

  @Test
  void keywordVectorAndHybridAreMeasuredOnTheSameHeldOutQueries() throws Exception {
    var dataset = QualityEvaluation.Dataset.read(DATASET);
    var stage = embeddings.stage();
    var space = stage.space().orElseThrow(() -> new IllegalStateException("embedding disabled"));
    var keyword = new InMemoryKeywordIndex("eval-keyword", 1_000, 100);
    var vectors = new InMemoryVectorIndex("eval-vector", space, 1_000, 100);
    var docs = dataset.documents();
    long embedStarted = System.nanoTime();
    for (int from = 0; from < docs.size(); from += 16) {
      var batch = docs.subList(from, Math.min(docs.size(), from + 16));
      var result = stage.embed(batch.stream().map(d -> d.title() + "\n" + d.text()).toList());
      if (!(result instanceof EmbeddingResult.Embedded embedded))
        throw new IllegalStateException("document embedding failed: " + result);
      var list = embedded.vectors();
      for (int i = 0; i < batch.size(); i++) {
        var d = batch.get(i);
        keyword.put(new IndexedDocument("eval", d.id(), d.title() + " " + d.text(), Map.of()));
        vectors.put("eval", d.id(), list.get(i), space, Map.of());
      }
    }
    long embedMs = (System.nanoTime() - embedStarted) / 1_000_000;
    var retriever = new HybridRetriever(keyword, Optional.of(vectors), stage, 60);

    var reports = new ArrayList<QualityEvaluation.Report>();
    for (var strategy :
        List.of(
            HybridResult.Strategy.KEYWORD_ONLY,
            HybridResult.Strategy.VECTOR_ONLY,
            HybridResult.Strategy.HYBRID)) {
      reports.add(
          QualityEvaluation.evaluate(
              dataset,
              strategy.name().toLowerCase(java.util.Locale.ROOT),
              q -> {
                var r =
                    retriever.retrieve(
                        "eval", q.text(), Filter.ALL, K, Duration.ofSeconds(60), strategy);
                return new QualityEvaluation.Outcome(
                    r.documents().stream().map(FusedDocument::id).toList(),
                    r.executed() != strategy,
                    "executed "
                        + r.executed()
                        + (r.degradations().isEmpty() ? "" : " " + r.degradations()));
              },
              K,
              "heldout"));
    }

    var out = Path.of("target", "evaluation");
    Files.createDirectories(out);
    var json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    var table = new StringBuilder();
    table
        .append("# Retrieval quality: keyword vs vector vs hybrid\n\n")
        .append("Dataset ")
        .append(dataset.id())
        .append(' ')
        .append(dataset.version())
        .append(", held-out split, k=")
        .append(K)
        .append(", embedding ")
        .append(space)
        .append(", ")
        .append(docs.size())
        .append(" documents embedded in ")
        .append(embedMs)
        .append(" ms.\n\n")
        .append(
            "| Strategy | P@5 | R@5 | MRR | nDCG@5 | Coverage | Negative FP rate | Degraded |\n"
                + "|---|---|---|---|---|---|---|---|\n");
    for (var report : reports) {
      json.writeValue(out.resolve("quality-" + report.strategy() + ".json").toFile(), report);
      var o = report.overall();
      table.append(
          String.format(
              java.util.Locale.ROOT,
              "| %s | %.3f | %.3f | %.3f | %.3f | %.3f | %s | %d |%n",
              report.strategy(),
              o.precisionAtK(),
              o.recallAtK(),
              o.mrr(),
              o.ndcgAtK(),
              o.coverage(),
              o.negativeFalsePositiveRate() == null
                  ? "n/a"
                  : String.format(java.util.Locale.ROOT, "%.2f", o.negativeFalsePositiveRate()),
              o.degraded()));
    }
    table.append(
        "\n## nDCG@5 by language\n\n| Strategy | en | fa | sv | mixed |\n|---|---|---|---|---|\n");
    for (var report : reports) {
      table.append("| ").append(report.strategy());
      for (String lang : List.of("en", "fa", "sv", "mixed"))
        table.append(
            String.format(
                java.util.Locale.ROOT, " | %.3f", report.byLanguage().get(lang).ndcgAtK()));
      table.append(" |\n");
    }
    table.append(
        "\n"
            + "## nDCG@5 by query kind\n\n"
            + "| Strategy | exact | semantic | multilingual | mixed-language |\n"
            + "|---|---|---|---|---|\n");
    for (var report : reports) {
      table.append("| ").append(report.strategy());
      for (String kind : List.of("exact", "semantic", "multilingual", "mixed-language"))
        table.append(
            String.format(java.util.Locale.ROOT, " | %.3f", report.byKind().get(kind).ndcgAtK()));
      table.append(" |\n");
    }
    Files.writeString(out.resolve("quality-comparison.md"), table.toString());
    System.out.println(table);

    for (var report : reports) {
      assertThat(report.overall().queries()).isEqualTo(27);
      assertThat(report.overall().degraded()).as(report.strategy() + " ran as requested").isZero();
    }
  }
}
