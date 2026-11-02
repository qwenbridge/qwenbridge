package io.qwenbridge.core.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Runs a retrieval strategy over a judged dataset and reports {@link RankingMetrics} (QB-209,
 * QB-210). Only the requested split is scored (normally {@code heldout}; {@code tuning} queries may
 * have been used to adjust ranking). Queries without relevant documents ("negative") are scored by
 * how many results they wrongly return, never by recall or nDCG.
 */
public final class QualityEvaluation {

  private QualityEvaluation() {}

  public record Document(String id, String domain, String language, String title, String text) {}

  public record Query(
      String id,
      String split,
      String domain,
      String language,
      String kind,
      String text,
      Map<String, Integer> judgments) {
    public Query {
      judgments = Map.copyOf(judgments == null ? Map.of() : judgments);
      RankingMetrics.judgments(judgments);
    }
  }

  public record Dataset(String id, String version, List<Document> documents, List<Query> queries) {
    public Dataset {
      documents = List.copyOf(documents);
      queries = List.copyOf(queries);
      var ids = new java.util.HashSet<String>();
      for (Document d : documents)
        if (!ids.add(d.id())) throw new IllegalArgumentException("Duplicate document " + d.id());
      for (Query q : queries)
        for (String judged : q.judgments().keySet())
          if (!ids.contains(judged))
            throw new IllegalArgumentException(q.id() + " judges unknown document " + judged);
    }

    public static Dataset read(Path file) throws IOException {
      return new ObjectMapper()
          .configure(
              com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              false)
          .readValue(file.toFile(), Dataset.class);
    }
  }

  /**
   * One strategy's answer to one query. {@code degraded} marks a strategy that did not fully run.
   */
  public record Outcome(List<String> ranked, boolean degraded, String note) {
    public Outcome {
      ranked = List.copyOf(ranked);
      note = note == null ? "" : note;
    }
  }

  public record QueryResult(
      String query,
      String language,
      String kind,
      String domain,
      List<String> ranked,
      Double precision,
      Double recall,
      Double reciprocalRank,
      Double ndcg,
      Integer falsePositives,
      boolean degraded,
      String note) {
    public QueryResult {
      ranked = List.copyOf(ranked);
    }
  }

  /**
   * Means over the queries with relevant documents; {@code coverage} = share of them with at least
   * one relevant result in the top K; {@code negativeFalsePositiveRate} = mean share of the top K
   * filled with results for queries that have no relevant document (null when there are none).
   */
  public record Summary(
      int queries,
      int scored,
      double precisionAtK,
      double recallAtK,
      double mrr,
      double ndcgAtK,
      double coverage,
      int degraded,
      Double negativeFalsePositiveRate) {}

  public record Report(
      String dataset,
      String version,
      String strategy,
      String split,
      int k,
      Summary overall,
      Map<String, Summary> byLanguage,
      Map<String, Summary> byKind,
      Map<String, Summary> byDomain,
      List<QueryResult> queries) {
    public Report {
      byLanguage = Map.copyOf(byLanguage);
      byKind = Map.copyOf(byKind);
      byDomain = Map.copyOf(byDomain);
      queries = List.copyOf(queries);
    }
  }

  public static Report evaluate(
      Dataset dataset, String strategy, Function<Query, Outcome> retrieve, int k, String split) {
    Objects.requireNonNull(retrieve, "retrieve must not be null");
    var results = new ArrayList<QueryResult>();
    for (Query q : dataset.queries()) {
      if (!q.split().equals(split)) continue;
      Outcome o = retrieve.apply(q);
      boolean negative = RankingMetrics.relevantCount(q.judgments()) == 0;
      results.add(
          negative
              ? new QueryResult(
                  q.id(),
                  q.language(),
                  q.kind(),
                  q.domain(),
                  o.ranked(),
                  null,
                  null,
                  null,
                  null,
                  RankingMetrics.falsePositives(o.ranked(), k),
                  o.degraded(),
                  o.note())
              : new QueryResult(
                  q.id(),
                  q.language(),
                  q.kind(),
                  q.domain(),
                  o.ranked(),
                  RankingMetrics.precisionAtK(q.judgments(), o.ranked(), k),
                  RankingMetrics.recallAtK(q.judgments(), o.ranked(), k),
                  RankingMetrics.reciprocalRank(q.judgments(), o.ranked()),
                  RankingMetrics.ndcgAtK(q.judgments(), o.ranked(), k),
                  null,
                  o.degraded(),
                  o.note()));
    }
    return new Report(
        dataset.id(),
        dataset.version(),
        strategy,
        split,
        k,
        summary(results, k),
        group(results, QueryResult::language, k),
        group(results, QueryResult::kind, k),
        group(results, QueryResult::domain, k),
        results);
  }

  static Map<String, Summary> group(
      List<QueryResult> results, Function<QueryResult, String> key, int k) {
    var groups = new TreeMap<String, List<QueryResult>>();
    for (var r : results) groups.computeIfAbsent(key.apply(r), x -> new ArrayList<>()).add(r);
    var out = new LinkedHashMap<String, Summary>();
    groups.forEach((name, list) -> out.put(name, summary(list, k)));
    return out;
  }

  static Summary summary(List<QueryResult> results, int k) {
    var scored = results.stream().filter(r -> r.ndcg() != null).toList();
    var negatives = results.stream().filter(r -> r.falsePositives() != null).toList();
    double n = Math.max(1, scored.size());
    return new Summary(
        results.size(),
        scored.size(),
        scored.stream().mapToDouble(QueryResult::precision).sum() / n,
        scored.stream().mapToDouble(QueryResult::recall).sum() / n,
        scored.stream().mapToDouble(QueryResult::reciprocalRank).sum() / n,
        scored.stream().mapToDouble(QueryResult::ndcg).sum() / n,
        scored.stream()
                .filter(
                    r ->
                        r.reciprocalRank() > 0
                            && r.ranked().size() > 0
                            && firstRelevantWithinK(r, k))
                .count()
            / n,
        (int) results.stream().filter(QueryResult::degraded).count(),
        negatives.isEmpty()
            ? null
            : negatives.stream().mapToDouble(r -> r.falsePositives() / (double) k).sum()
                / negatives.size());
  }

  private static boolean firstRelevantWithinK(QueryResult r, int k) {
    return 1.0 / r.reciprocalRank() <= k;
  }
}
