package io.qwenbridge.core.retrieval.hybrid;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.ScoredDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal rank fusion (QB-113): {@code score(d) = Σ 1 / (k + rank_s(d))} over the sources that
 * returned d. It uses ranks only, so incomparable scores (BM25 and cosine) are never added up.
 * Duplicates collapse into one document that keeps each source's rank. Ties break by the best
 * single-source rank, then by id, so the order is fully deterministic.
 */
public final class RankFusion {

  public static final int DEFAULT_K = 60;
  public static final String SCORER = "rrf";

  private RankFusion() {}

  /** One source's ranked list. */
  public record SourceList(String source, List<ScoredDocument> hits) {
    public SourceList {
      hits = List.copyOf(hits);
    }
  }

  public static List<FusedDocument> rrf(List<SourceList> lists, int k, int topK) {
    if (k < 1 || topK < 1) {
      throw new IllegalArgumentException("k and topK must be positive");
    }
    record Acc(
        String id,
        List<SourceRank> sources,
        Map<String, io.qwenbridge.core.retrieval.filter.FieldValue> fields,
        double score) {}
    Map<String, Acc> byId = new LinkedHashMap<>();
    for (SourceList list : lists) {
      for (ScoredDocument hit : list.hits()) {
        Acc acc = byId.get(hit.id());
        List<SourceRank> sources = acc == null ? new ArrayList<>() : new ArrayList<>(acc.sources());
        if (sources.stream().anyMatch(s -> s.source().equals(list.source()))) {
          continue; // A source lists each document once; ignore a duplicate within one list.
        }
        sources.add(new SourceRank(list.source(), hit.rank(), hit.score()));
        double score = (acc == null ? 0 : acc.score()) + 1.0 / (k + hit.rank());
        byId.put(
            hit.id(), new Acc(hit.id(), sources, acc == null ? hit.fields() : acc.fields(), score));
      }
    }
    List<Acc> ordered = new ArrayList<>(byId.values());
    ordered.sort(
        Comparator.comparingDouble(Acc::score)
            .reversed()
            .thenComparingInt(
                a -> a.sources().stream().mapToInt(SourceRank::rank).min().orElseThrow())
            .thenComparing(Acc::id));
    List<FusedDocument> out = new ArrayList<>();
    for (int i = 0; i < Math.min(topK, ordered.size()); i++) {
      Acc a = ordered.get(i);
      out.add(
          new FusedDocument(
              a.id(),
              i + 1,
              new RetrievalScore(a.score(), RetrievalScore.Kind.HYBRID_FUSION, SCORER),
              a.sources(),
              a.fields()));
    }
    return out;
  }
}
