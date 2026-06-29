package io.qwenbridge.analysis.cache.coalescing;

import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AIAnalysisSingleFlightTest {

    private final AIAnalysisSingleFlight singleFlight =
            new AIAnalysisSingleFlight();

    @Test
    void shouldExecuteSupplierOnlyOnceForSameConcurrentKey() throws Exception {
        CacheKey key = new CacheKey("same-key");
        AtomicInteger calls = new AtomicInteger();

        int requestCount = 100;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<java.util.concurrent.Future<SearchAnalysis>> futures =
                    new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);

                    return singleFlight.execute(key, () -> {
                        calls.incrementAndGet();
                        sleep(150);
                        return SearchAnalysis.fallback("desk");
                    });
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            for (var future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isNotNull();
            }
        }

        assertThat(calls).hasValue(1);
        assertThat(singleFlight.inFlightCount()).isZero();
    }

    @Test
    void shouldExecuteSeparatelyForDifferentKeys() {
        AtomicInteger calls = new AtomicInteger();

        SearchAnalysis first = singleFlight.execute(
                new CacheKey("key-1"),
                () -> {
                    calls.incrementAndGet();
                    return SearchAnalysis.fallback("desk");
                }
        );

        SearchAnalysis second = singleFlight.execute(
                new CacheKey("key-2"),
                () -> {
                    calls.incrementAndGet();
                    return SearchAnalysis.fallback("chair");
                }
        );

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(calls).hasValue(2);
        assertThat(singleFlight.inFlightCount()).isZero();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
