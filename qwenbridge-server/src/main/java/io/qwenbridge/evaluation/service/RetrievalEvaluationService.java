package io.qwenbridge.evaluation.service;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.EvaluationResult;
import java.util.List;
import java.util.Map;

public interface RetrievalEvaluationService {

  EvaluationResult evaluate(
      List<EvaluationQuery> queries, Map<String, List<String>> rankedResultsByQueryId, int k);
}
