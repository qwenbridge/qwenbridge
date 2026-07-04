package io.qwenbridge.sdk.search;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SearchAnalyzeResponse(
        String requestId,
        long processingTimeMs,
        String originalQuery,
        String language,
        String intent,
        String decision,
        double confidence,
        List<String> rewrites,
        List<String> threatReasons,
        boolean semanticValidated,
        double semanticScore,
        boolean policyPassed,
        List<String> policyViolations,
        JsonNode executionPlan,
        JsonNode executionResult,
        JsonNode search,
        JsonNode cache,
        JsonNode pipelineTrace
) {
}
