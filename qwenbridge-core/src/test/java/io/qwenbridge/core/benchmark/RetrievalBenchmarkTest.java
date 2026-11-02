package io.qwenbridge.core.benchmark;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.QueryVector;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.retrieval.hybrid.HybridRetriever;
import io.qwenbridge.core.retrieval.hybrid.RankFusion;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-216 – QB-218, QB-224: latency and throughput of the Core in-process retrieval engine (BM25,
 * exact cosine kNN, RRF hybrid) on deterministic synthetic corpora, across index size, vector
 * dimension, top-K and concurrency. Query vectors are precomputed, so vector numbers exclude
 * embedding generation (measured separately with the real model in AiTransformationBenchmarkTest
 * and the HTTP suite). Enabled by QWENBRIDGE_RUN_BENCHMARKS=true; QWENBRIDGE_BENCHMARK_PROFILE=full
 * runs every size, otherwise a quick subset for the regression gate. Results:
 * target/benchmarks/core-retrieval.json; a deliberately slowed copy (a fixed delay added to every
 * keyword call: 5 ms, or QWENBRIDGE_BENCHMARK_INJECTED_DELAY_MS) goes to core-retrieval-slowed.json
 * so the gate can be shown to catch a regression. On a shared CI runner the keyword p95 itself is
 * 10-15 ms and noisy, so the master workflow injects a larger delay.
 */
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_BENCHMARKS", matches = "true")
class RetrievalBenchmarkTest {

  static final boolean FULL = "full".equals(System.getenv("QWENBRIDGE_BENCHMARK_PROFILE"));
  static final int VOCABULARY = 5_000;
  static final long INJECTED_DELAY_MS =
      Long.parseLong(
          java.util.Objects.requireNonNullElse(
              System.getenv("QWENBRIDGE_BENCHMARK_INJECTED_DELAY_MS"), "5"));

  record Result(
      String workload,
      String strategy,
      int documents,
      int dimensions,
      int topK,
      int concurrency,
      int queries,
      double p50Ms,
      double p95Ms,
      double p99Ms,
      double meanMs,
      double throughputQps) {}

  /** Deterministic text: Zipf-like word choice from a fixed vocabulary. */
  static String text(Random random, int words) {
    var b = new StringBuilder();
    for (int i = 0; i < words; i++) {
      int w =
          (int)
              Math.min(VOCABULARY - 1, Math.floor(Math.pow(random.nextDouble(), 2.5) * VOCABULARY));
      b.append('w').append(w).append(' ');
    }
    return b.toString().trim();
  }

  static double[] vector(Random random, int dims) {
    var v = new double[dims];
    for (int i = 0; i < dims; i++) v[i] = random.nextGaussian();
    return v;
  }

  static InMemoryKeywordIndex keywordIndex(int documents) {
    var random = new Random(42);
    var index = new InMemoryKeywordIndex("bench-kw", documents, 200);
    for (int i = 0; i < documents; i++)
      index.put(new IndexedDocument("t", "d" + i, text(random, 30), Map.of()));
    return index;
  }

  static InMemoryVectorIndex vectorIndex(int documents, EmbeddingSpace space) {
    var random = new Random(43);
    var index = new InMemoryVectorIndex("bench-vec", space, documents, 200);
    for (int i = 0; i < documents; i++)
      index.put("t", "d" + i, vector(random, space.dimensions()), space, Map.of());
    return index;
  }

  /** Runs {@code queries} calls at {@code concurrency} after a warm-up, timing each call. */
  static Result measure(
      String workload,
      String strategy,
      int documents,
      int dims,
      int topK,
      int concurrency,
      int queries,
      IntFunction<Callable<Object>> call)
      throws Exception {
    for (int i = 0; i < Math.min(200, queries); i++) call.apply(i).call();
    long[] latencies = new long[queries];
    long started = System.nanoTime();
    try (var pool = Executors.newFixedThreadPool(concurrency)) {
      var futures = new ArrayList<Future<?>>();
      for (int t = 0; t < concurrency; t++) {
        int thread = t;
        futures.add(
            pool.submit(
                () -> {
                  for (int i = thread; i < queries; i += concurrency) {
                    long s = System.nanoTime();
                    call.apply(i + 1_000).call();
                    latencies[i] = System.nanoTime() - s;
                  }
                  return null;
                }));
      }
      for (var f : futures) f.get();
    }
    double wall = (System.nanoTime() - started) / 1e9;
    Arrays.sort(latencies);
    double mean = Arrays.stream(latencies).average().orElse(0) / 1e6;
    return new Result(
        workload,
        strategy,
        documents,
        dims,
        topK,
        concurrency,
        queries,
        pct(latencies, 0.50),
        pct(latencies, 0.95),
        pct(latencies, 0.99),
        mean,
        queries / wall);
  }

  static double pct(long[] sorted, double p) {
    int index = (int) Math.ceil(p * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(sorted.length - 1, index))] / 1e6;
  }

  static List<String> queryTexts(int n) {
    var random = new Random(7);
    var out = new ArrayList<String>();
    for (int i = 0; i < n; i++) out.add(text(random, 2 + random.nextInt(3)));
    return out;
  }

  /** Embedding provider that returns precomputed query vectors instantly (no model cost). */
  static EmbeddingProvider precomputed(EmbeddingSpace space) {
    var random = new Random(11);
    var cache = new java.util.concurrent.ConcurrentHashMap<String, double[]>();
    return new EmbeddingProvider() {
      public String id() {
        return "precomputed";
      }

      public EmbeddingModel model() {
        return new EmbeddingModel(space, 64, 8000);
      }

      public EmbeddingResult embed(List<String> texts, Duration timeout) {
        var vectors = new ArrayList<double[]>();
        for (String t : texts)
          vectors.add(
              cache.computeIfAbsent(
                  t,
                  k -> {
                    synchronized (random) {
                      return vector(random, space.dimensions());
                    }
                  }));
        return new EmbeddingResult.Embedded(vectors, space);
      }
    };
  }

  /** Adds a fixed delay to every call: the controlled regression of Scenario J. */
  static SearchProvider slowed(SearchProvider delegate, long delayMs) {
    return new SearchProvider() {
      public String id() {
        return delegate.id();
      }

      public ProviderCapabilities capabilities() {
        return delegate.capabilities();
      }

      public SearchOutcome search(SearchRequest request, CancellationToken cancellation) {
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return delegate.search(request, cancellation);
      }
    };
  }

  @Test
  void coreRetrievalLatencyAndThroughputAreMeasured() throws Exception {
    var results = new ArrayList<Result>();
    var texts = queryTexts(512);
    int queries = FULL ? 4_000 : 1_000;
    int[] concurrencies = FULL ? new int[] {1, 8, 32} : new int[] {1, 8};
    int[] keywordSizes = FULL ? new int[] {1_000, 10_000, 100_000} : new int[] {10_000};
    for (int size : keywordSizes) {
      var index = keywordIndex(size);
      for (int c : concurrencies)
        results.add(
            measure(
                "keyword",
                "keyword",
                size,
                0,
                10,
                c,
                queries,
                i ->
                    () ->
                        index.search(
                            SearchRequest.keyword("t", texts.get(i % texts.size()), Filter.ALL, 10),
                            CancellationToken.create())));
    }
    record VectorCase(int size, int dims) {}
    var vectorCases =
        FULL
            ? List.of(
                new VectorCase(1_000, 384),
                new VectorCase(10_000, 384),
                new VectorCase(50_000, 384),
                new VectorCase(10_000, 1024))
            : List.of(new VectorCase(10_000, 384));
    for (var vc : vectorCases) {
      var space = new EmbeddingSpace("bench", "1", vc.dims());
      var index = vectorIndex(vc.size(), space);
      var random = new Random(5);
      var qv = new ArrayList<QueryVector>();
      for (int i = 0; i < 256; i++) qv.add(new QueryVector(vector(random, vc.dims()), space));
      for (int topK : FULL ? new int[] {5, 20, 100} : new int[] {20})
        for (int c : concurrencies)
          results.add(
              measure(
                  "vector",
                  "vector",
                  vc.size(),
                  vc.dims(),
                  topK,
                  c,
                  Math.min(queries, vc.size() >= 50_000 ? 1_000 : queries),
                  i ->
                      () ->
                          index.search(
                              SearchRequest.vector("t", qv.get(i % qv.size()), Filter.ALL, topK),
                              CancellationToken.create())));
    }
    int hybridSize = 10_000;
    var hybridSpace = new EmbeddingSpace("bench", "1", 384);
    var keyword = keywordIndex(hybridSize);
    var vectors = vectorIndex(hybridSize, hybridSpace);
    var stage = new EmbeddingStage(Optional.of(precomputed(hybridSpace)), Duration.ofSeconds(5));
    var hybrid = new HybridRetriever(keyword, Optional.of(vectors), stage, 60);
    for (int c : concurrencies)
      results.add(
          measure(
              "hybrid",
              "hybrid",
              hybridSize,
              384,
              10,
              c,
              queries,
              i ->
                  () ->
                      hybrid.retrieve(
                          "t",
                          texts.get(i % texts.size()),
                          Filter.ALL,
                          10,
                          Duration.ofSeconds(5),
                          HybridResult.Strategy.HYBRID)));
    // Fusion alone: RRF over two 30-candidate lists (the candidate count hybrid uses for top-10).
    var left =
        ((SearchOutcome.Success)
                keyword.search(
                    SearchRequest.keyword("t", texts.get(0), Filter.ALL, 30),
                    CancellationToken.create()))
            .hits();
    var right =
        ((SearchOutcome.Success)
                vectors.search(
                    SearchRequest.vector(
                        "t",
                        new QueryVector(vector(new Random(9), 384), hybridSpace),
                        Filter.ALL,
                        30),
                    CancellationToken.create()))
            .hits();
    results.add(
        measure(
            "fusion",
            "rrf",
            60,
            0,
            10,
            1,
            queries,
            i ->
                () ->
                    RankFusion.rrf(
                        List.of(
                            new RankFusion.SourceList("kw", left),
                            new RankFusion.SourceList("vec", right)),
                        60,
                        10)));

    var slowed = new ArrayList<Result>();
    var slowIndex = slowed(keyword, INJECTED_DELAY_MS);
    slowed.add(
        measure(
            "keyword",
            "keyword",
            hybridSize,
            0,
            10,
            8,
            Math.min(queries, 1_000),
            i ->
                () ->
                    slowIndex.search(
                        SearchRequest.keyword("t", texts.get(i % texts.size()), Filter.ALL, 10),
                        CancellationToken.create())));

    var out = Path.of("target", "benchmarks");
    Files.createDirectories(out);
    var json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    var env = new LinkedHashMap<String, Object>();
    env.put("profile", FULL ? "full" : "quick");
    env.put("java", Runtime.version().toString());
    env.put("availableProcessors", Runtime.getRuntime().availableProcessors());
    env.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
    env.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    json.writeValue(
        out.resolve("core-retrieval.json").toFile(),
        Map.of("environment", env, "results", results));
    json.writeValue(
        out.resolve("core-retrieval-slowed.json").toFile(),
        Map.of(
            "environment",
            env,
            "results",
            slowed,
            "note",
            INJECTED_DELAY_MS + " ms added to every keyword call"));
    for (var r : results)
      System.out.printf(
          java.util.Locale.ROOT,
          "core-benchmark %-8s docs=%-6d dims=%-4d topK=%-3d c=%-2d p50=%.3fms p95=%.3fms"
              + " p99=%.3fms qps=%.0f%n",
          r.workload(),
          r.documents(),
          r.dimensions(),
          r.topK(),
          r.concurrency(),
          r.p50Ms(),
          r.p95Ms(),
          r.p99Ms(),
          r.throughputQps());
    assertTrue(results.stream().allMatch(r -> r.throughputQps() > 0 && r.p95Ms() >= r.p50Ms()));
  }
}
