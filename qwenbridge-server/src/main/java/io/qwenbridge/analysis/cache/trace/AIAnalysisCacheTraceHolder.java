package io.qwenbridge.analysis.cache.trace;

import org.springframework.stereotype.Component;

@Component
public class AIAnalysisCacheTraceHolder {

    private static final ThreadLocal<AIAnalysisCacheTrace> CURRENT =
            new ThreadLocal<>();

    public void set(AIAnalysisCacheTrace trace) {
        CURRENT.set(trace);
    }

    public AIAnalysisCacheTrace get() {
        AIAnalysisCacheTrace trace = CURRENT.get();
        return trace == null ? AIAnalysisCacheTrace.disabled() : trace;
    }

    public void clear() {
        CURRENT.remove();
    }
}
