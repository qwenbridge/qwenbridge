package io.omnisearch.model;

import io.omnisearch.decision.DecisionType;
import io.omnisearch.pipeline.result.PipelineTraceItem;

import java.util.List;

public record SearchAnalyzeResponse(
        String requestId,
        long processingTimeMs,
        String originalQuery,
        String language,
        String intent,
        DecisionType decision,
        double confidence,
        List<String> rewrites,
        List<String> threatReasons,
        boolean semanticValidated,
        double semanticScore,
        boolean policyPassed,
        List<String> policyViolations,
        List<PipelineTraceItem> pipelineTrace
) {}
