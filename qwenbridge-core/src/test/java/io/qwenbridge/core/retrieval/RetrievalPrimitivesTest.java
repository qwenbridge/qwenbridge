package io.qwenbridge.core.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.embedding.IndexCompatibility;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** QB-100 (filter AST), QB-107–QB-111. */
class RetrievalPrimitivesTest {

  static Map<String, FieldValue> fields(Object... kv) {
    Map<String, FieldValue> m = new java.util.HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      Object v = kv[i + 1];
      m.put(
          (String) kv[i],
          v instanceof String s && !s.matches("-?\\d+(\\.\\d+)?")
              ? new FieldValue.Text(s)
              : v instanceof List<?> l
                  ? new FieldValue.TextList(l.stream().map(Object::toString).toList())
                  : new FieldValue.Number(new BigDecimal(v.toString())));
    }
    return m;
  }

  static List<String> ids(SearchOutcome outcome) {
    return assertInstanceOf(SearchOutcome.Success.class, outcome).hits().stream()
        .map(ScoredDocument::id)
        .toList();
  }

  @Nested
  class FilterSemantics {

    final Map<String, FieldValue> shoe =
        fields(
            "brand",
            "Nike",
            "price",
            "89.90",
            "material",
            "mesh",
            "tags",
            List.of("running", "black"));

    @Test
    void comparisonsAreExactAndTyped() {
      assertTrue(
          FilterEvaluator.matches(
              new Filter.Compare("price", Filter.Compare.Op.LT, FieldValue.number("90")), shoe));
      assertFalse(
          FilterEvaluator.matches(
              new Filter.Compare("price", Filter.Compare.Op.LT, FieldValue.number("89.9")), shoe));
      assertTrue(
          FilterEvaluator.matches(
              new Filter.Compare("price", Filter.Compare.Op.EQ, FieldValue.number("89.900")),
              shoe));
      assertFalse(
          FilterEvaluator.matches(
              new Filter.Compare("brand", Filter.Compare.Op.GT, FieldValue.text("A")), shoe));
      assertTrue(
          FilterEvaluator.matches(
              new Filter.Compare("tags", Filter.Compare.Op.EQ, FieldValue.text("black")), shoe));
    }

    @Test
    void exclusionsMatchDocumentsWithoutTheField() {
      Filter notLeather =
          new Filter.Compare("material", Filter.Compare.Op.NE, FieldValue.text("leather"));
      assertTrue(FilterEvaluator.matches(notLeather, shoe));
      assertTrue(FilterEvaluator.matches(notLeather, fields("brand", "Puma")));
      assertTrue(FilterEvaluator.matches(new Filter.Not(new Filter.Exists("color")), shoe));
      assertFalse(
          FilterEvaluator.matches(
              new Filter.Compare("color", Filter.Compare.Op.EQ, FieldValue.text("red")), shoe));
    }

    @Test
    void booleanStructureAndFeatures() {
      Filter f =
          new Filter.And(
              List.of(
                  new Filter.Or(
                      List.of(
                          new Filter.In(
                              "brand", List.of(FieldValue.text("Nike"), FieldValue.text("Adidas"))),
                          new Filter.Contains("material", "leather"))),
                  new Filter.Compare("price", Filter.Compare.Op.LTE, FieldValue.number("100"))));
      assertTrue(FilterEvaluator.matches(f, shoe));
      assertEquals(
          Set.of(FilterFeature.OR, FilterFeature.IN, FilterFeature.CONTAINS, FilterFeature.RANGE),
          f.features());
    }
  }

  @Nested
  class KeywordIndex {

    InMemoryKeywordIndex index() {
      InMemoryKeywordIndex index = new InMemoryKeywordIndex("kw", 10_000, 100);
      index.put(
          new IndexedDocument(
              "acme", "d1", "black running shoes", fields("brand", "Nike", "price", "90")));
      index.put(
          new IndexedDocument(
              "acme", "d2", "black leather boots", fields("brand", "Ecco", "price", "150")));
      index.put(
          new IndexedDocument(
              "acme", "d3", "running socks running", fields("brand", "Nike", "price", "10")));
      index.put(
          new IndexedDocument(
              "acme", "d4", "کفش ورزشی مشکی", fields("brand", "Nike", "price", "70")));
      index.put(
          new IndexedDocument("globex", "g1", "black running shoes", fields("brand", "Nike")));
      return index;
    }

    @Test
    void rankedByBm25WithFiltersAndScoresTypedAsRetrievalScores() {
      SearchOutcome outcome =
          index()
              .search(
                  SearchRequest.keyword("acme", "running shoes", Filter.ALL, 10),
                  CancellationToken.create());
      List<String> ranked = ids(outcome);
      assertEquals("d1", ranked.getFirst(), "matching both terms beats repeating one");
      assertTrue(ranked.containsAll(List.of("d1", "d3")));
      ScoredDocument top = ((SearchOutcome.Success) outcome).hits().getFirst();
      assertEquals(RetrievalScore.Kind.BM25, top.score().kind());
      assertEquals(1, top.rank());

      Filter cheapNike =
          new Filter.And(
              List.of(
                  new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Nike")),
                  new Filter.Compare("price", Filter.Compare.Op.LT, FieldValue.number("50"))));
      assertEquals(
          List.of("d3"),
          ids(
              index()
                  .search(
                      SearchRequest.keyword("acme", "running", cheapNike, 10),
                      CancellationToken.create())));
    }

    @Test
    void tenantsAreIsolatedIncludingTheirTermStatistics() {
      InMemoryKeywordIndex index = index();
      assertEquals(
          List.of(),
          ids(
              index.search(
                  SearchRequest.keyword("other", "black", Filter.ALL, 10),
                  CancellationToken.create())));
      assertEquals(
          List.of("g1"),
          ids(
              index.search(
                  SearchRequest.keyword("globex", "black running", Filter.ALL, 10),
                  CancellationToken.create())));
      double before = score(index, "acme", "boots");
      for (int i = 0; i < 50; i++) {
        index.put(new IndexedDocument("globex", "b" + i, "boots boots", Map.of()));
      }
      assertEquals(
          before, score(index, "acme", "boots"), 1e-12, "another tenant must not shift ranking");
    }

    private double score(InMemoryKeywordIndex index, String tenant, String q) {
      return ((SearchOutcome.Success)
              index.search(
                  SearchRequest.keyword(tenant, q, Filter.ALL, 10), CancellationToken.create()))
          .hits()
          .getFirst()
          .score()
          .value();
    }

    @Test
    void equalScoresTieBreakByIdDeterministically() {
      InMemoryKeywordIndex index = new InMemoryKeywordIndex("kw", 100, 10);
      for (String id : List.of("c", "a", "b")) {
        index.put(new IndexedDocument("t", id, "same text", Map.of()));
      }
      assertEquals(
          List.of("a", "b", "c"),
          ids(
              index.search(
                  SearchRequest.keyword("t", "same", Filter.ALL, 10), CancellationToken.create())));
    }

    @Test
    void multilingualTokensAndReplacementAndRemoval() {
      InMemoryKeywordIndex index = index();
      assertEquals(
          List.of("d4"),
          ids(
              index.search(
                  SearchRequest.keyword("acme", "کفش", Filter.ALL, 10),
                  CancellationToken.create())));
      index.put(new IndexedDocument("acme", "d4", "sandals", fields("brand", "Nike")));
      assertEquals(
          List.of(),
          ids(
              index.search(
                  SearchRequest.keyword("acme", "کفش", Filter.ALL, 10),
                  CancellationToken.create())));
      assertTrue(index.remove("acme", "d1"));
      assertFalse(index.remove("acme", "d1"));
      assertFalse(
          ids(index.search(
                  SearchRequest.keyword("acme", "shoes", Filter.ALL, 10),
                  CancellationToken.create()))
              .contains("d1"));
    }

    @Test
    void boundsAndUnsupportedRequestsAreTyped() {
      InMemoryKeywordIndex small = new InMemoryKeywordIndex("kw", 1, 5);
      small.put(new IndexedDocument("t", "x", "a", Map.of()));
      assertThrows(
          IllegalStateException.class,
          () -> small.put(new IndexedDocument("t", "y", "b", Map.of())));
      assertInstanceOf(
          SearchOutcome.Unsupported.class,
          small.search(SearchRequest.keyword("t", "a", Filter.ALL, 6), CancellationToken.create()));
      EmbeddingSpace space = new EmbeddingSpace("m", "1", 2);
      assertInstanceOf(
          SearchOutcome.Unsupported.class,
          small.search(
              SearchRequest.vector("t", new QueryVector(new double[] {1, 0}, space), Filter.ALL, 1),
              CancellationToken.create()));
    }

    @Test
    void cancellationIsHonoured() {
      InMemoryKeywordIndex index = new InMemoryKeywordIndex("kw", 5_000, 10);
      for (int i = 0; i < 2_000; i++) {
        index.put(new IndexedDocument("t", "d" + i, "word " + i, Map.of()));
      }
      CancellationToken token = CancellationToken.create();
      token.cancel();
      SearchOutcome outcome =
          index.search(SearchRequest.keyword("t", "word", Filter.ALL, 10), token);
      assertEquals(
          SearchOutcome.Failure.CANCELLED,
          assertInstanceOf(SearchOutcome.Failed.class, outcome).failure());
    }
  }

  @Nested
  class VectorIndex {

    final EmbeddingSpace space = new EmbeddingSpace("bge-m3", "1", 3);

    InMemoryVectorIndex index() {
      InMemoryVectorIndex index = new InMemoryVectorIndex("vec", space, 1000, 50);
      index.put("acme", "north", new double[] {0, 1, 0}, space, fields("brand", "A"));
      index.put("acme", "east", new double[] {1, 0, 0}, space, fields("brand", "B"));
      index.put("acme", "northeast", new double[] {1, 1, 0}, space, fields("brand", "A"));
      index.put("globex", "north", new double[] {0, 1, 0}, space, Map.of());
      return index;
    }

    QueryVector q(double... v) {
      return new QueryVector(v, space);
    }

    @Test
    void cosineRankingWithFiltersAndIsolation() {
      SearchOutcome outcome =
          index()
              .search(
                  SearchRequest.vector("acme", q(0, 2, 0), Filter.ALL, 10),
                  CancellationToken.create());
      assertEquals(List.of("north", "northeast", "east"), ids(outcome));
      ScoredDocument top = ((SearchOutcome.Success) outcome).hits().getFirst();
      assertEquals(1.0, top.score().value(), 1e-12);
      assertEquals(RetrievalScore.Kind.VECTOR_SIMILARITY, top.score().kind());
      Filter brandA = new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("A"));
      assertEquals(
          List.of("northeast", "north"),
          ids(
              index()
                  .search(
                      SearchRequest.vector("acme", q(1, 0, 0), brandA, 10),
                      CancellationToken.create())));
      assertEquals(
          List.of("north"),
          ids(
              index()
                  .search(
                      SearchRequest.vector("globex", q(1, 0, 0), Filter.ALL, 10),
                      CancellationToken.create())));
    }

    @Test
    void vectorsFromAnotherSpaceAreNeverCompared() {
      InMemoryVectorIndex index = index();
      EmbeddingSpace other = new EmbeddingSpace("bge-m3", "2", 3);
      assertThrows(
          IllegalArgumentException.class,
          () -> index.put("acme", "x", new double[] {1, 0, 0}, other, Map.of()));
      assertThrows(
          IllegalArgumentException.class,
          () -> index.put("acme", "x", new double[] {1, 0}, space, Map.of()));
      assertThrows(
          IllegalArgumentException.class,
          () -> index.put("acme", "x", new double[] {Double.NaN, 0, 0}, space, Map.of()));
      assertInstanceOf(
          SearchOutcome.Unsupported.class,
          index.search(
              SearchRequest.vector(
                  "acme", new QueryVector(new double[] {1, 0, 0}, other), Filter.ALL, 5),
              CancellationToken.create()));
      assertEquals(
          SearchOutcome.Failure.INVALID_REQUEST,
          ((SearchOutcome.Failed)
                  index.search(
                      SearchRequest.vector("acme", q(0, 0, 0), Filter.ALL, 5),
                      CancellationToken.create()))
              .failure());
      IndexCompatibility c = index.compatibility(new EmbeddingSpace("e5", "1", 768));
      assertTrue(c.reindexRequired());
      assertEquals(
          List.of(
              IndexCompatibility.Reason.MODEL_CHANGED,
              IndexCompatibility.Reason.DIMENSIONS_CHANGED),
          c.reindexReasons());
      assertTrue(index.compatibility(space).compatible());
    }

    @Test
    void concurrentWritesAndReadsStayConsistent() throws Exception {
      InMemoryVectorIndex index = new InMemoryVectorIndex("vec", space, 10_000, 10);
      ExecutorService pool = Executors.newFixedThreadPool(8);
      try {
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
          int worker = t;
          futures.add(
              pool.submit(
                  () -> {
                    for (int i = 0; i < 250; i++) {
                      index.put(
                          "t",
                          worker + "-" + i,
                          new double[] {1 + i, worker + 1, 1},
                          space,
                          Map.of());
                      index.search(
                          SearchRequest.vector("t", q(1, 1, 1), Filter.ALL, 5),
                          CancellationToken.create());
                    }
                  }));
        }
        for (Future<?> f : futures) {
          f.get();
        }
      } finally {
        pool.shutdownNow();
      }
      assertEquals(2000, index.size());
      assertTrue(index.remove("t", "0-0"));
      assertEquals(1999, index.size());
    }
  }

  @Nested
  class Embeddings {

    final EmbeddingModel model = new EmbeddingModel(new EmbeddingSpace("m", "1", 2), 4, 20);

    EmbeddingProvider provider(BiFunction<List<String>, Duration, EmbeddingResult> fn) {
      return new EmbeddingProvider() {
        public String id() {
          return "fixture";
        }

        public EmbeddingModel model() {
          return model;
        }

        public EmbeddingResult embed(List<String> texts, Duration timeout) {
          return fn.apply(texts, timeout);
        }
      };
    }

    EmbeddingStage stage(BiFunction<List<String>, Duration, EmbeddingResult> fn) {
      return new EmbeddingStage(Optional.of(provider(fn)), Duration.ofMillis(300));
    }

    EmbeddingResult.Failure failure(EmbeddingResult r) {
      return assertInstanceOf(EmbeddingResult.Failed.class, r).failure();
    }

    @Test
    void validVectorsPassThroughInOrder() {
      EmbeddingStage s =
          stage(
              (texts, t) ->
                  new EmbeddingResult.Embedded(
                      texts.stream().map(x -> new double[] {x.length(), 1}).toList(),
                      model.space()));
      EmbeddingResult.Embedded e =
          assertInstanceOf(EmbeddingResult.Embedded.class, s.embed(List.of("a", "bbb")));
      assertEquals(3.0, e.vectors().get(1)[0]);
      assertEquals(Optional.of(model.space()), s.space());
    }

    @Test
    void everyFailureIsTyped() {
      assertEquals(
          EmbeddingResult.Failure.NO_PROVIDER,
          failure(new EmbeddingStage(Optional.empty(), Duration.ofSeconds(1)).embed(List.of("a"))));
      EmbeddingStage ok =
          stage(
              (texts, t) ->
                  new EmbeddingResult.Embedded(List.of(new double[] {1, 1}), model.space()));
      assertEquals(
          EmbeddingResult.Failure.BATCH_TOO_LARGE,
          failure(ok.embed(List.of("a", "b", "c", "d", "e"))));
      assertEquals(
          EmbeddingResult.Failure.INPUT_TOO_LARGE, failure(ok.embed(List.of("x".repeat(21)))));
      assertEquals(
          EmbeddingResult.Failure.INVALID_OUTPUT,
          failure(ok.embed(List.of("a", "b"))),
          "wrong count");
      assertEquals(
          EmbeddingResult.Failure.INVALID_OUTPUT,
          failure(
              stage(
                      (texts, t) ->
                          new EmbeddingResult.Embedded(
                              List.of(new double[] {1, 1, 1}), model.space()))
                  .embed(List.of("a"))),
          "wrong dimensions");
      assertEquals(
          EmbeddingResult.Failure.INVALID_OUTPUT,
          failure(
              stage(
                      (texts, t) ->
                          new EmbeddingResult.Embedded(
                              List.of(new double[] {1, 1}), new EmbeddingSpace("m", "2", 2)))
                  .embed(List.of("a"))),
          "another space");
      assertEquals(
          EmbeddingResult.Failure.UNAVAILABLE,
          failure(
              stage(
                      (texts, t) -> {
                        throw new IllegalStateException("down");
                      })
                  .embed(List.of("a"))));
      assertEquals(
          EmbeddingResult.Failure.TIMEOUT,
          failure(
              stage(
                      (texts, t) -> {
                        try {
                          Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return null;
                      })
                  .embed(List.of("a"))));
    }
  }
}
