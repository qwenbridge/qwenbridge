package io.qwenbridge.evaluation.service;

import io.qwenbridge.evaluation.metrics.RetrievalEvaluationMetrics;
import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.EvaluationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DefaultRetrievalEvaluationService implements RetrievalEvaluationService {

    private final RetrievalEvaluationMetrics metrics;

    public DefaultRetrievalEvaluationService() {
        this(new RetrievalEvaluationMetrics());
    }

    DefaultRetrievalEvaluationService(RetrievalEvaluationMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public EvaluationResult evaluate(
            List<EvaluationQuery> queries,
            Map<String, List<String>> rankedResultsByQueryId,
            int k
    ) {
        Objects.requireNonNull(queries, "queries must not be null");
        Objects.requireNonNull(rankedResultsByQueryId, "rankedResultsByQueryId must not be null");

        if (queries.isEmpty() || k <= 0) {
            return new EvaluationResult(0, 0.0, 0.0, 0.0, 0.0);
        }

        double precisionTotal = 0.0;
        double recallTotal = 0.0;
        double reciprocalRankTotal = 0.0;
        double ndcgTotal = 0.0;

        for (EvaluationQuery query : queries) {
            List<String> rankedResults =
                    rankedResultsByQueryId.getOrDefault(query.id(), List.of());

            precisionTotal += metrics.precisionAtK(query, rankedResults, k);
            recallTotal += metrics.recallAtK(query, rankedResults, k);
            reciprocalRankTotal += metrics.reciprocalRank(query, rankedResults);
            ndcgTotal += metrics.ndcgAtK(query, rankedResults, k);
        }

        int queryCount = queries.size();

        return new EvaluationResult(
                queryCount,
                precisionTotal / queryCount,
                recallTotal / queryCount,
                reciprocalRankTotal / queryCount,
                ndcgTotal / queryCount
        );
    }
}
