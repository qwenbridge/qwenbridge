package io.qwenbridge.operations.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OperationsMetricsTest {

    @Test
    void shouldUseStableMetricNamesAndTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationsMetrics metrics = new OperationsMetrics(registry);

        metrics.recordHttpRequest("GET", "/api/v1/health", 200, Duration.ofMillis(12));
        metrics.recordAiProvider("ollama", "chat", "success", Duration.ofMillis(20));
        metrics.recordOpenSearch("search", "timeout", Duration.ofMillis(30));
        metrics.incrementCache("analysis", "hit");
        metrics.incrementRateLimit("default", "allowed");

        Timer http = registry.find("qwenbridge.http.server.requests")
                .tags("method", "GET", "path", "/api/v1/health", "status", "200")
                .timer();
        assertNotNull(http);
        assertEquals(1, http.count());

        Timer ai = registry.find("qwenbridge.ai.provider.requests")
                .tags("provider", "ollama", "operation", "chat", "outcome", "success")
                .timer();
        assertNotNull(ai);
        assertEquals(1, ai.count());

        Timer openSearch = registry.find("qwenbridge.opensearch.requests")
                .tags("operation", "search", "outcome", "timeout")
                .timer();
        assertNotNull(openSearch);
        assertEquals(1, openSearch.count());

        Counter cache = registry.find("qwenbridge.redis.cache.events")
                .tags("cache", "analysis", "result", "hit")
                .counter();
        assertNotNull(cache);
        assertEquals(1.0, cache.count());

        Counter rateLimit = registry.find("qwenbridge.ratelimit.decisions")
                .tags("policy", "default", "decision", "allowed")
                .counter();
        assertNotNull(rateLimit);
        assertEquals(1.0, rateLimit.count());
    }

    @Test
    void shouldTrackSseLifecycleWithoutNegativeActiveSessions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationsMetrics metrics = new OperationsMetrics(registry);

        metrics.sessionOpened();
        metrics.sessionOpened();
        metrics.sessionClosed("completed");
        metrics.sessionClosed("disconnect");
        metrics.sessionClosed("duplicate-cleanup");
        metrics.recordSseEvent("ai.token");
        metrics.recordSseEvent(null);

        Gauge active = registry.find("qwenbridge.sse.sessions.active").gauge();
        assertNotNull(active);
        assertEquals(0.0, active.value());

        Counter opened = registry.find("qwenbridge.sse.sessions.opened").counter();
        assertNotNull(opened);
        assertEquals(2.0, opened.count());

        Counter completed = registry.find("qwenbridge.sse.sessions.closed")
                .tag("reason", "completed")
                .counter();
        assertNotNull(completed);
        assertEquals(1.0, completed.count());

        Counter token = registry.find("qwenbridge.sse.events")
                .tag("event", "ai.token")
                .counter();
        assertNotNull(token);
        assertEquals(1.0, token.count());

        Counter unknown = registry.find("qwenbridge.sse.events")
                .tag("event", "unknown")
                .counter();
        assertNotNull(unknown);
        assertEquals(1.0, unknown.count());
    }
}
