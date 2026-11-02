package io.qwenbridge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.evaluation.QualityEvaluation;
import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.intent.IntentAnalyzer;
import io.qwenbridge.language.LanguageService;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-219: AI-assisted and language stages measured separately from retrieval, with the configured
 * providers. Deterministic in-process stages (language detection, intent analysis, constraint
 * extraction) at concurrency 1 and 8; the analysis model with unique queries (no cache hit) at
 * concurrency 1, 2 and 4; embedding generation at batch sizes 1, 8 and 32. Model identities come
 * from the provider. The first call after a model switch (Ollama may keep one model loaded) is
 * reported separately as "cold". Optional translation is reported as not configured unless set.
 * Enabled by QWENBRIDGE_RUN_AI_BENCHMARKS and QWENBRIDGE_RUN_OLLAMA_IT (about 3.5 minutes).
 * Results: target/benchmarks/ai-transformations.json.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_AI_BENCHMARKS", matches = "true")
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class AiTransformationBenchmarkTest {

  record Stage(
      String stage,
      String provider,
      int concurrency,
      int batch,
      int calls,
      int failures,
      double p50Ms,
      double p95Ms,
      double p99Ms,
      double meanMs,
      double throughputPerSecond) {}

  @Inject LanguageService languages;
  @Inject SearchAnalysisService analysis;
  @Inject EmbeddingStages embeddings;
  @Inject io.qwenbridge.translation.TranslationStages translations;

  @ConfigProperty(name = "qwenbridge.ai.ollama.base-url")
  String ollama;

  @ConfigProperty(name = "qwenbridge.ai.ollama.chat-model", defaultValue = "")
  String chatModel;

  @ConfigProperty(name = "qwenbridge.ai.ollama.embedding-model", defaultValue = "")
  String embeddingModel;

  @ConfigProperty(name = "qwenbridge.translation.provider", defaultValue = "none")
  String translation;

  static Stage measure(
      String stage,
      String provider,
      int concurrency,
      int batch,
      int calls,
      IntFunction<Callable<Boolean>> call)
      throws Exception {
    long[] latency = new long[calls];
    boolean[] ok = new boolean[calls];
    long started = System.nanoTime();
    try (var pool = Executors.newFixedThreadPool(concurrency)) {
      var futures = new ArrayList<Future<?>>();
      for (int t = 0; t < concurrency; t++) {
        int thread = t;
        futures.add(
            pool.submit(
                () -> {
                  for (int i = thread; i < calls; i += concurrency) {
                    long s = System.nanoTime();
                    ok[i] = call.apply(i).call();
                    latency[i] = System.nanoTime() - s;
                  }
                  return null;
                }));
      }
      for (var f : futures) f.get();
    }
    double wall = (System.nanoTime() - started) / 1e9;
    int failures = 0;
    for (boolean b : ok) if (!b) failures++;
    Arrays.sort(latency);
    return new Stage(
        stage,
        provider,
        concurrency,
        batch,
        calls,
        failures,
        pct(latency, .5),
        pct(latency, .95),
        pct(latency, .99),
        Arrays.stream(latency).average().orElse(0) / 1e6,
        calls * (double) batch / wall);
  }

  static double pct(long[] sorted, double p) {
    return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1))]
        / 1e6;
  }

  @Test
  void aiAndLanguageStagesAreMeasuredSeparatelyFromRetrieval() throws Exception {
    var dataset =
        QualityEvaluation.Dataset.read(
            Path.of("..", "evaluation", "datasets", "multidomain-1.0.json"));
    List<String> texts = dataset.queries().stream().map(QualityEvaluation.Query::text).toList();
    var results = new ArrayList<Stage>();
    var intents = new IntentAnalyzer();
    for (int c : new int[] {1, 8}) {
      results.add(
          measure(
              "language-detection",
              "lingua (in-process)",
              c,
              1,
              2_000,
              i -> () -> languages.analyze(texts.get(i % texts.size()), Optional.empty()) != null));
      results.add(
          measure(
              "intent-analysis",
              "core rules (in-process)",
              c,
              1,
              2_000,
              i ->
                  () ->
                      intents.analyze(
                              texts.get(i % texts.size()),
                              Optional.empty(),
                              Set.of("commerce"),
                              List.of())
                          != null));
      results.add(
          measure(
              "constraint-extraction",
              "core rules (in-process)",
              c,
              1,
              2_000,
              i ->
                  () ->
                      ConstraintExtractor.extract(
                              texts.get(i % texts.size()), ExtractionContext.none())
                          != null));
    }
    String run = Long.toString(System.nanoTime(), 36);
    // Ollama may hold one model at a time: the first call after a switch pays the model load.
    // It is measured on its own ("cold"), then the warm measurements follow.
    results.add(
        measure(
            "model-analysis-cold",
            "ollama " + chatModel,
            1,
            1,
            1,
            i -> () -> analysis.analyze("warm-up " + run) != null));
    for (int c : new int[] {1, 2, 4}) {
      int calls = c == 4 ? 12 : 8;
      int level = c;
      results.add(
          measure(
              "model-analysis",
              "ollama " + chatModel,
              c,
              1,
              calls,
              i ->
                  () -> {
                    // A unique suffix defeats the analysis cache, so every call reaches the model.
                    var a =
                        analysis.analyze(
                            texts.get(i % texts.size()) + " (" + run + "-" + level + "-" + i + ")");
                    return a != null && !"UNAVAILABLE".equals(a.modelThreatDecision());
                  }));
    }
    // Commerce v2 translates every non-English query with the model (TranslationStages.model()),
    // independently of qwenbridge.translation.provider (which governs only the v1 pipeline).
    var nonEnglish =
        dataset.queries().stream()
            .filter(q -> q.language().equals("fa") || q.language().equals("sv"))
            .toList();
    var english = io.qwenbridge.core.request.LanguageTag.parse("en");
    for (int c : new int[] {1, 2}) {
      int level = c;
      results.add(
          measure(
              "commerce-model-translation",
              "ollama " + chatModel + " (TranslationStages.model)",
              c,
              1,
              8,
              i ->
                  () -> {
                    var q = nonEnglish.get(i % nonEnglish.size());
                    var outcome =
                        translations
                            .model()
                            .translate(
                                q.text() + " " + level + i,
                                io.qwenbridge.core.request.LanguageTag.parse(q.language())
                                    .orElseThrow(),
                                english,
                                io.qwenbridge.core.extraction.EntityDictionary.EMPTY,
                                io.qwenbridge.core.translation.TranslationStage.Policy.REPORT);
                    return outcome.result()
                        instanceof io.qwenbridge.core.translation.TranslationResult.Translated;
                  }));
    }
    var stage = embeddings.stage();
    // Analysis calls abandoned by their client timeout keep running inside the model server (one
    // request at a time), so later calls queue behind them. Measure how long that backlog takes to
    // drain (until a single embedding answers within 1 s), then measure embeddings unqueued.
    long drainStarted = System.nanoTime();
    double drainMs = -1;
    while ((System.nanoTime() - drainStarted) / 1e9 < 240) {
      long s0 = System.nanoTime();
      stage.embed(List.of("drain " + run));
      if ((System.nanoTime() - s0) / 1e6 < 1_000) {
        drainMs = (System.nanoTime() - drainStarted) / 1e6;
        break;
      }
    }
    results.add(
        new Stage(
            "model-backlog-drain",
            "ollama (after abandoned analysis calls)",
            1,
            1,
            1,
            drainMs < 0 ? 1 : 0,
            drainMs,
            drainMs,
            drainMs,
            drainMs,
            0));
    results.add(
        measure(
            "embedding-cold",
            "ollama " + embeddingModel,
            1,
            1,
            1,
            i -> () -> stage.embed(List.of("warm-up " + run)) instanceof EmbeddingResult.Embedded));
    for (int batch : new int[] {1, 8, 32}) {
      results.add(
          measure(
              "embedding",
              "ollama " + embeddingModel,
              1,
              batch,
              10,
              i ->
                  () ->
                      stage.embed(
                              texts.subList(0, Math.min(batch, texts.size())).stream()
                                  .map(t -> t + " " + i)
                                  .toList())
                          instanceof EmbeddingResult.Embedded));
    }
    results.add(
        measure(
            "embedding",
            "ollama " + embeddingModel,
            4,
            1,
            40,
            i ->
                () ->
                    stage.embed(List.of(texts.get(i % texts.size()) + " c4-" + i))
                        instanceof EmbeddingResult.Embedded));

    var models = new LinkedHashMap<String, Object>();
    try (var http = HttpClient.newHttpClient()) {
      var tags =
          http.send(
              HttpRequest.newBuilder(URI.create(ollama + "/api/tags")).build(),
              HttpResponse.BodyHandlers.ofString());
      for (var m : new ObjectMapper().readTree(tags.body()).path("models"))
        models.put(
            m.path("name").asText(),
            Map.of(
                "digest",
                m.path("digest").asText(),
                "parameters",
                m.path("details").path("parameter_size").asText(),
                "quantization",
                m.path("details").path("quantization_level").asText()));
    }
    var report = new LinkedHashMap<String, Object>();
    report.put(
        "providers",
        Map.of(
            "chat",
            "ollama " + chatModel,
            "embedding",
            "ollama " + embeddingModel,
            "endpoint",
            ollama,
            "translation (v1 pipeline)",
            "qwenbridge.translation.provider=" + translation,
            "translation (Commerce v2)",
            "model, always (measured as commerce-model-translation)",
            "external providers",
            "none configured; not measured"));
    report.put("models", models);
    report.put("results", results);
    Files.createDirectories(Path.of("target", "benchmarks"));
    new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValue(Path.of("target", "benchmarks", "ai-transformations.json").toFile(), report);
    for (var r : results)
      System.out.printf(
          java.util.Locale.ROOT,
          "ai-benchmark %-22s c=%d batch=%-2d calls=%-4d failures=%d p50=%.2fms p95=%.2fms"
              + " throughput=%.1f/s%n",
          r.stage(),
          r.concurrency(),
          r.batch(),
          r.calls(),
          r.failures(),
          r.p50Ms(),
          r.p95Ms(),
          r.throughputPerSecond());
    assertThat(results).allMatch(r -> r.calls() > 0);
  }
}
