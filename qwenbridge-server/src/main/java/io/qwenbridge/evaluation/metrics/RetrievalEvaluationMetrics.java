package io.qwenbridge.evaluation.metrics;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import java.util.List;
import java.util.Map;

public class RetrievalEvaluationMetrics {

  public double precisionAtK(EvaluationQuery query, List<String> rankedDocumentIds, int k) {
    if (k <= 0 || rankedDocumentIds == null || rankedDocumentIds.isEmpty()) {
      return 0.0;
    }

    Map<String, ?> labels = query.labelsByDocumentId();

    long relevantRetrieved =
        rankedDocumentIds.stream()
            .limit(k)
            .filter(
                documentId -> {
                  var label = labels.get(documentId);
                  return label
                          instanceof io.qwenbridge.evaluation.model.RelevanceLabel relevanceLabel
                      && relevanceLabel.relevant();
                })
            .count();

    return relevantRetrieved / (double) k;
  }

  public double recallAtK(EvaluationQuery query, List<String> rankedDocumentIds, int k) {
    long relevantDocumentCount = query.relevantDocumentCount();

    if (k <= 0
        || relevantDocumentCount == 0
        || rankedDocumentIds == null
        || rankedDocumentIds.isEmpty()) {
      return 0.0;
    }

    Map<String, ?> labels = query.labelsByDocumentId();

    long relevantRetrieved =
        rankedDocumentIds.stream()
            .limit(k)
            .filter(
                documentId -> {
                  var label = labels.get(documentId);
                  return label
                          instanceof io.qwenbridge.evaluation.model.RelevanceLabel relevanceLabel
                      && relevanceLabel.relevant();
                })
            .count();

    return relevantRetrieved / (double) relevantDocumentCount;
  }

  public double reciprocalRank(EvaluationQuery query, List<String> rankedDocumentIds) {
    if (rankedDocumentIds == null || rankedDocumentIds.isEmpty()) {
      return 0.0;
    }

    Map<String, ?> labels = query.labelsByDocumentId();

    for (int index = 0; index < rankedDocumentIds.size(); index++) {
      var label = labels.get(rankedDocumentIds.get(index));

      if (label instanceof io.qwenbridge.evaluation.model.RelevanceLabel relevanceLabel
          && relevanceLabel.relevant()) {
        return 1.0 / (index + 1);
      }
    }

    return 0.0;
  }

  public double ndcgAtK(EvaluationQuery query, List<String> rankedDocumentIds, int k) {
    if (k <= 0 || rankedDocumentIds == null || rankedDocumentIds.isEmpty()) {
      return 0.0;
    }

    double dcg = dcg(query, rankedDocumentIds.stream().limit(k).toList());

    List<String> idealRanking =
        query.labels().stream()
            .filter(io.qwenbridge.evaluation.model.RelevanceLabel::relevant)
            .sorted((first, second) -> Integer.compare(second.relevance(), first.relevance()))
            .map(io.qwenbridge.evaluation.model.RelevanceLabel::documentId)
            .limit(k)
            .toList();

    double idcg = dcg(query, idealRanking);

    if (idcg == 0.0) {
      return 0.0;
    }

    return dcg / idcg;
  }

  private double dcg(EvaluationQuery query, List<String> documentIds) {
    Map<String, io.qwenbridge.evaluation.model.RelevanceLabel> labels = query.labelsByDocumentId();

    double total = 0.0;

    for (int index = 0; index < documentIds.size(); index++) {
      var label = labels.get(documentIds.get(index));
      int relevance = label == null ? 0 : label.relevance();

      double gain = Math.pow(2.0, relevance) - 1.0;
      double discount = log2(index + 2.0);

      total += gain / discount;
    }

    return total;
  }

  private double log2(double value) {
    return Math.log(value) / Math.log(2.0);
  }
}
