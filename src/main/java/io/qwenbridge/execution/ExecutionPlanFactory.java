package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExecutionPlanFactory {

    public ExecutionPlan from(SearchDecision decision) {
        List<ExecutionStep> steps = new ArrayList<>();
        int order = 10;

        if (decision.rewriteAgain()) {
            steps.add(new ExecutionStep(order, ExecutionOperation.REWRITE_QUERY, "Decision requested another rewrite."));
            order += 10;
        }

        if (decision.answer()) {
            steps.add(new ExecutionStep(order, ExecutionOperation.DIRECT_ANSWER, "Decision requested direct answer."));
            return new ExecutionPlan(decision.mode(), decision.backend(), steps, decision.reason());
        }

        if (decision.hybridSearch()) {
            steps.add(new ExecutionStep(order, ExecutionOperation.HYBRID_SEARCH, "Decision requested hybrid search."));
            order += 10;
        } else {
            if (decision.keywordSearch()) {
                steps.add(new ExecutionStep(order, ExecutionOperation.KEYWORD_SEARCH, "Decision requested keyword search."));
                order += 10;
            }

            if (decision.vectorSearch()) {
                steps.add(new ExecutionStep(order, ExecutionOperation.VECTOR_SEARCH, "Decision requested vector search."));
                order += 10;
            }
        }

        if (decision.facets()) {
            steps.add(new ExecutionStep(order, ExecutionOperation.APPLY_FACETS, "Decision requested facets."));
            order += 10;
        }

        if (decision.rerank()) {
            steps.add(new ExecutionStep(order, ExecutionOperation.RERANK_RESULTS, "Decision requested reranking."));
            order += 10;
        }

        steps.add(new ExecutionStep(order, ExecutionOperation.RETURN_RESULTS, "Return search results."));

        return new ExecutionPlan(decision.mode(), decision.backend(), steps, decision.reason());
    }
}
