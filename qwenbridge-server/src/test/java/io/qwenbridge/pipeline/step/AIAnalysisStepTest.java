package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTraceHolder;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIAnalysisStepTest {

    private final AIAnalysisCacheTraceHolder traceHolder =
            new AIAnalysisCacheTraceHolder();

    @Test
    void shouldStoreCacheTraceInExecutionContext() {
        SearchAnalysisService service = query -> {
            traceHolder.set(AIAnalysisCacheTrace.hit(
                    "cache-key",
                    "ollama",
                    "qwen2.5",
                    "v4"
            ));
            return SearchAnalysis.fallback(query);
        };

        AIAnalysisStep step = new AIAnalysisStep(service, traceHolder);
        ExecutionContext context = new ExecutionContext("desk");

        SearchAnalysis result = step.execute(context);

        AIAnalysisCacheTrace trace =
                context.get(AIAnalysisCacheTrace.class);

        assertThat(result).isNotNull();
        assertThat(trace.hit()).isTrue();
        assertThat(trace.key()).isEqualTo("cache-key");
    }

    @Test
    void shouldClearTraceHolderAfterExecution() {
        SearchAnalysisService service = query -> {
            traceHolder.set(AIAnalysisCacheTrace.miss(
                    "cache-key",
                    "ollama",
                    "qwen2.5",
                    "v4"
            ));
            return SearchAnalysis.fallback(query);
        };

        AIAnalysisStep step = new AIAnalysisStep(service, traceHolder);
        ExecutionContext context = new ExecutionContext("desk");

        step.execute(context);

        assertThat(traceHolder.get().enabled()).isFalse();
    }
}
