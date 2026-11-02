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
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Degradation;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Strategy;
import io.qwenbridge.core.retrieval.hybrid.HybridRetriever;
import io.qwenbridge.core.retrieval.hybrid.RankFusion;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.ManagedVectorIndex;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** QB-112–QB-116 with real (hashing) vectors on a synthetic multilingual catalog. */
class HybridRetrievalTest {

  static final String T = "acme";
  static final List<IndexedDocument> CATALOG =
      List.of(
          doc("p01", "Wireless noise cancelling headphones", "audio", "199"),
          doc("p02", "Wired studio headphones", "audio", "99"),
          doc("p03", "Bluetooth speaker waterproof", "audio", "79"),
          doc("p04", "Running shoes black mesh", "shoes", "89"),
          doc("p05", "Leather boots brown", "shoes", "149"),
          doc("p06", "Trail running shoes waterproof", "shoes", "129"),
          doc("p07", "Mechanical keyboard wireless", "computers", "119"),
          doc("p08", "Gaming mouse wireless", "computers", "59"),
          doc("p09", "هدفون بی‌سیم ورزشی", "audio", "89"),
          doc("p10", "Trådlösa hörlurar med brusreducering", "audio", "179"),
          doc("p11", "Laptop sleeve 14 inch", "computers", "25"),
          doc("p12", "Yoga mat non slip", "sports", "35"));

  static IndexedDocument doc(String id, String title, String category, String price) {
    return new IndexedDocument(
        T,
        id,
        title,
        Map.of(
            "category",
            new FieldValue.Text(category),
            "price",
            new FieldValue.Number(new BigDecimal(price))));
  }

  static final HashingEmbeddingProvider V1 = new HashingEmbeddingProvider("1", 256);

  static EmbeddingStage stage(EmbeddingProvider p) {
    return new EmbeddingStage(Optional.ofNullable(p), Duration.ofSeconds(5));
  }

  static InMemoryKeywordIndex keyword() {
    InMemoryKeywordIndex k = new InMemoryKeywordIndex("keyword", 1000, 200);
    CATALOG.forEach(k::put);
    return k;
  }

  static ManagedVectorIndex vectors(EmbeddingProvider p) {
    ManagedVectorIndex v = new ManagedVectorIndex("vector", p.model().space(), stage(p), 1000, 200);
    assertInstanceOf(EmbeddingResult.Embedded.class, v.upsert(CATALOG));
    return v;
  }

  static List<String> ids(HybridResult r) {
    return r.documents().stream().map(FusedDocument::id).toList();
  }

  @Nested
  class Fusion {

    static ScoredDocument hit(String id, int rank, double score, RetrievalScore.Kind kind) {
      return new ScoredDocument(id, rank, new RetrievalScore(score, kind, kind.name()), Map.of());
    }

    @Test
    void reciprocalRankFusionUsesRanksDedupesAndKeepsProvenance() {
      var bm25 =
          new RankFusion.SourceList(
              "keyword",
              List.of(
                  hit("a", 1, 12.0, RetrievalScore.Kind.BM25),
                  hit("b", 2, 9.0, RetrievalScore.Kind.BM25)));
      var cosine =
          new RankFusion.SourceList(
              "vector",
              List.of(
                  hit("b", 1, 0.91, RetrievalScore.Kind.VECTOR_SIMILARITY),
                  hit("c", 2, 0.80, RetrievalScore.Kind.VECTOR_SIMILARITY)));
      List<FusedDocument> fused = RankFusion.rrf(List.of(bm25, cosine), 60, 10);
      assertEquals(List.of("b", "a", "c"), fused.stream().map(FusedDocument::id).toList());
      FusedDocument b = fused.getFirst();
      assertEquals(1.0 / 62 + 1.0 / 61, b.fusedScore().value(), 1e-12);
      assertEquals(RetrievalScore.Kind.HYBRID_FUSION, b.fusedScore().kind());
      assertEquals(2, b.sources().size());
      assertEquals(9.0, b.sources().getFirst().score().value(), "original scores are preserved");
      assertEquals(0.91, b.sources().get(1).score().value());
    }

    @Test
    void tiesBreakByBestSourceRankThenId() {
      var one =
          new RankFusion.SourceList(
              "s1",
              List.of(
                  hit("z", 1, 1, RetrievalScore.Kind.BM25),
                  hit("m", 2, 1, RetrievalScore.Kind.BM25)));
      var two =
          new RankFusion.SourceList(
              "s2",
              List.of(
                  hit("m", 1, 1, RetrievalScore.Kind.BM25),
                  hit("z", 2, 1, RetrievalScore.Kind.BM25)));
      assertEquals(
          List.of("m", "z"),
          RankFusion.rrf(List.of(one, two), 60, 10).stream().map(FusedDocument::id).toList());
      assertEquals(
          RankFusion.rrf(List.of(one, two), 60, 10).stream().map(FusedDocument::id).toList(),
          RankFusion.rrf(List.of(two, one), 60, 10).stream().map(FusedDocument::id).toList());
    }
  }

  @Nested
  class HybridWithRealVectors {

    @Test
    void vectorsRecoverWhatKeywordsMissAndFusionKeepsAgreement() {
      HybridRetriever retriever =
          new HybridRetriever(keyword(), Optional.of(vectors(V1)), stage(V1), 60);
      HybridResult typo =
          retriever.retrieve(
              T, "wireles headphone", Filter.ALL, 3, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.HYBRID, typo.executed());
      assertFalse(typo.degraded());
      assertEquals("p01", typo.documents().getFirst().id(), ids(typo).toString());
      assertTrue(
          typo.documents().getFirst().sources().stream().anyMatch(s -> s.source().equals("vector")),
          "the typo is recovered by the vector side");

      HybridResult exact =
          retriever.retrieve(
              T, "running shoes", Filter.ALL, 3, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(List.of("p04", "p06"), ids(exact).subList(0, 2).stream().sorted().toList());
      assertEquals(
          2, exact.documents().getFirst().sources().size(), "both sources agree on the top result");
    }

    @Test
    void filtersAndTenantApplyToBothSides() {
      HybridRetriever retriever =
          new HybridRetriever(keyword(), Optional.of(vectors(V1)), stage(V1), 60);
      Filter cheapAudio =
          new Filter.And(
              List.of(
                  new Filter.Compare("category", Filter.Compare.Op.EQ, FieldValue.text("audio")),
                  new Filter.Compare("price", Filter.Compare.Op.LT, FieldValue.number("100"))));
      HybridResult r =
          retriever.retrieve(
              T, "wireless headphones", cheapAudio, 10, Duration.ofSeconds(5), Strategy.HYBRID);
      assertTrue(ids(r).containsAll(List.of("p02")));
      assertFalse(ids(r).contains("p01"), "199 is not under 100");
      assertFalse(ids(r).contains("p07"), "keyboards are not audio");
      assertEquals(
          List.of(),
          ids(
              retriever.retrieve(
                  "globex",
                  "wireless headphones",
                  Filter.ALL,
                  10,
                  Duration.ofSeconds(5),
                  Strategy.HYBRID)));
    }
  }

  @Nested
  class LabelledFallback {

    @Test
    void withoutEmbeddingsTheResultIsLabelledKeywordOnly() {
      HybridRetriever retriever =
          new HybridRetriever(keyword(), Optional.of(vectors(V1)), stage(null), 60);
      HybridResult r =
          retriever.retrieve(
              T, "wireless headphones", Filter.ALL, 5, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.HYBRID, r.requested());
      assertEquals(Strategy.KEYWORD_ONLY, r.executed());
      assertEquals(Degradation.Reason.EMBEDDING_UNAVAILABLE, r.degradations().getFirst().reason());
      assertTrue(
          r.documents().stream()
              .allMatch(d -> d.sources().stream().allMatch(s -> s.source().equals("keyword"))));
    }

    @Test
    void anIndexFromAnotherEmbeddingSpaceIsNotQueriedUntilRebuilt() {
      HashingEmbeddingProvider v2 = new HashingEmbeddingProvider("2", 256);
      ManagedVectorIndex index = vectors(V1);
      HybridRetriever retriever = new HybridRetriever(keyword(), Optional.of(index), stage(v2), 60);
      HybridResult stale =
          retriever.retrieve(
              T, "wireless headphones", Filter.ALL, 5, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.KEYWORD_ONLY, stale.executed());
      assertEquals(Degradation.Reason.REINDEX_REQUIRED, stale.degradations().getFirst().reason());

      ManagedVectorIndex rebuiltTarget =
          new ManagedVectorIndex("vector", V1.model().space(), stage(v2), 1000, 200);
      assertTrue(rebuiltTarget.compatibility().reindexRequired());
      ManagedVectorIndex.RebuildResult rebuilt = rebuiltTarget.rebuild(CATALOG, 5);
      assertTrue(rebuilt.swapped());
      assertEquals(CATALOG.size(), rebuilt.documents());
      assertTrue(rebuiltTarget.compatibility().compatible());
      HybridResult fresh =
          new HybridRetriever(keyword(), Optional.of(rebuiltTarget), stage(v2), 60)
              .retrieve(
                  T, "wireless headphones", Filter.ALL, 5, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.HYBRID, fresh.executed());
    }

    @Test
    void oneFailingSideIsRecordedAndBothFailingReturnsNothing() {
      SearchProvider broken =
          new SearchProvider() {
            public String id() {
              return "broken";
            }

            public ProviderCapabilities capabilities() {
              return vectors(V1).capabilities();
            }

            public SearchOutcome search(SearchRequest r, CancellationToken c) {
              throw new IllegalStateException("down");
            }
          };
      HybridResult partial =
          new HybridRetriever(keyword(), Optional.of(broken), stage(V1), 60)
              .retrieve(T, "headphones", Filter.ALL, 5, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.KEYWORD_ONLY, partial.executed());
      assertEquals(Degradation.Reason.PROVIDER_FAILED, partial.degradations().getFirst().reason());

      SearchProvider brokenKeyword =
          new SearchProvider() {
            public String id() {
              return "broken-keyword";
            }

            public ProviderCapabilities capabilities() {
              return keyword().capabilities();
            }

            public SearchOutcome search(SearchRequest r, CancellationToken c) {
              return new SearchOutcome.Failed(SearchOutcome.Failure.TIMEOUT, "slow");
            }
          };
      HybridResult none =
          new HybridRetriever(brokenKeyword, Optional.of(broken), stage(V1), 60)
              .retrieve(T, "headphones", Filter.ALL, 5, Duration.ofSeconds(5), Strategy.HYBRID);
      assertEquals(Strategy.NONE, none.executed());
      assertEquals(2, none.degradations().size());
      assertTrue(none.documents().isEmpty());
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void searchesKeepWorkingDuringARebuildAndAFailedRebuildChangesNothing() throws Exception {
      ManagedVectorIndex index = vectors(V1);
      ExecutorService pool = Executors.newFixedThreadPool(4);
      AtomicBoolean failed = new AtomicBoolean();
      try {
        List<Future<?>> readers = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
          readers.add(
              pool.submit(
                  () -> {
                    for (int i = 0; i < 200; i++) {
                      var q =
                          ((EmbeddingResult.Embedded)
                                  V1.embed(List.of("headphones"), Duration.ofSeconds(1)))
                              .vectors()
                              .getFirst();
                      SearchOutcome o =
                          index.search(
                              SearchRequest.vector(
                                  T, new QueryVector(q, V1.model().space()), Filter.ALL, 3),
                              CancellationToken.create());
                      if (!(o instanceof SearchOutcome.Success s) || s.hits().isEmpty()) {
                        failed.set(true);
                      }
                    }
                  }));
        }
        for (int r = 0; r < 5; r++) {
          assertTrue(index.rebuild(CATALOG, 4).swapped());
        }
        for (Future<?> f : readers) {
          f.get();
        }
      } finally {
        pool.shutdownNow();
      }
      assertFalse(failed.get(), "no search failed or came back empty during rebuilds");

      EmbeddingProvider failing =
          new EmbeddingProvider() {
            public String id() {
              return "failing";
            }

            public EmbeddingModel model() {
              return V1.model();
            }

            public EmbeddingResult embed(List<String> texts, Duration timeout) {
              return new EmbeddingResult.Failed(EmbeddingResult.Failure.UNAVAILABLE, "failing");
            }
          };
      ManagedVectorIndex guarded =
          new ManagedVectorIndex("vector", V1.model().space(), stage(failing), 100, 10);
      assertFalse(guarded.rebuild(CATALOG, 4).swapped());
      assertEquals(0, guarded.size());
    }

    @Test
    void deleteAndClose() {
      ManagedVectorIndex index = vectors(V1);
      assertTrue(index.delete(T, "p01"));
      assertEquals(CATALOG.size() - 1, index.size());
      index.close();
      assertEquals(ManagedVectorIndex.State.CLOSED, index.state());
      var q =
          ((EmbeddingResult.Embedded) V1.embed(List.of("x"), Duration.ofSeconds(1)))
              .vectors()
              .getFirst();
      assertEquals(
          SearchOutcome.Failure.UNAVAILABLE,
          ((SearchOutcome.Failed)
                  index.search(
                      SearchRequest.vector(
                          T, new QueryVector(q, V1.model().space()), Filter.ALL, 3),
                      CancellationToken.create()))
              .failure());
      assertThrows(IllegalStateException.class, () -> index.upsert(CATALOG));
      assertThrows(IllegalStateException.class, () -> index.delete(T, "p02"));
    }
  }
}
