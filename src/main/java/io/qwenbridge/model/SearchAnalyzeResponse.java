package io.qwenbridge.model;

import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.pipeline.result.PipelineTraceItem;

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
        ExecutionPlanResponse executionPlan,
        ExecutionResultResponse executionResult,
        List<PipelineTraceItem> pipelineTrace
) {}
