package io.qwenbridge.commerce;

import io.qwenbridge.evaluation.metrics.RetrievalEvaluationMetrics;
import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.RelevanceLabel;
import java.util.List;
import java.util.Map;

public final class CommerceEvaluation {
  private CommerceEvaluation() {}

  public record Judgment(String queryId, List<String> rankedIds, Map<String, Integer> relevance) {
    public Judgment {
      rankedIds = List.copyOf(rankedIds);
      relevance = Map.copyOf(relevance);
      if (queryId == null
          || queryId.isBlank()
          || rankedIds.size() > 1000
          || relevance.size() > 1000
          || rankedIds.stream().distinct().count() != rankedIds.size())
        throw new IllegalArgumentException("Invalid relevance judgment");
    }
  }

  public record RetrievalReport(
      int queries, int k, double precisionAtK, double recallAtK, double mrr, double ndcgAtK) {}

  public record Observation(double signal, boolean correct) {
    public Observation {
      if (!Double.isFinite(signal) || signal < 0 || signal > 1)
        throw new IllegalArgumentException("Invalid confidence signal");
    }
  }

  public record CalibrationReport(
      int observations, double brierScore, double expectedCalibrationError) {}

  public static RetrievalReport retrieval(List<Judgment> judgments, int k) {
    if (judgments.isEmpty() || judgments.size() > 1000 || k < 1 || k > 1000)
      throw new IllegalArgumentException("Invalid evaluation batch");
    var metrics = new RetrievalEvaluationMetrics();
    double p = 0, r = 0, m = 0, n = 0;
    for (var item : judgments) {
      var query =
          new EvaluationQuery(
              item.queryId(),
              item.queryId(),
              item.relevance().entrySet().stream()
                  .map(e -> new RelevanceLabel(e.getKey(), e.getValue()))
                  .toList());
      p += metrics.precisionAtK(query, item.rankedIds(), k);
      r += metrics.recallAtK(query, item.rankedIds(), k);
      m += metrics.reciprocalRank(query, item.rankedIds());
      n += metrics.ndcgAtK(query, item.rankedIds(), k);
    }
    int count = judgments.size();
    return new RetrievalReport(count, k, p / count, r / count, m / count, n / count);
  }

  /**
   * Brier score and ECE, computed by the Core {@link io.qwenbridge.core.confidence.Calibration}
   * (QB-083). This endpoint reports ECE for any batch size, as before; callers judge whether the
   * batch is large enough.
   */
  public static CalibrationReport calibration(List<Observation> data) {
    if (data.isEmpty() || data.size() > 10000)
      throw new IllegalArgumentException("Invalid calibration batch");
    var report =
        io.qwenbridge.core.confidence.Calibration.report(
            data.stream()
                .map(
                    o ->
                        new io.qwenbridge.core.confidence.Calibration.Observation(
                            o.signal(), o.correct()))
                .toList(),
            10,
            1);
    return new CalibrationReport(
        report.observations(),
        report.brierScore(),
        report.expectedCalibrationError().getAsDouble());
  }
}
