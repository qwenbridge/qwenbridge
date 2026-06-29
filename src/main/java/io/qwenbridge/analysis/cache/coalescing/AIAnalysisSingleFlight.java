package io.qwenbridge.analysis.cache.coalescing;

import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class AIAnalysisSingleFlight {

    private final ConcurrentHashMap<CacheKey, CompletableFuture<SearchAnalysis>> inFlight =
            new ConcurrentHashMap<>();

    public SearchAnalysis execute(CacheKey key, Supplier<SearchAnalysis> supplier) {
        Objects.requireNonNull(key, "cache key must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        CompletableFuture<SearchAnalysis> future =
                inFlight.computeIfAbsent(key, ignored -> {
                    CompletableFuture<SearchAnalysis> created =
                            CompletableFuture.supplyAsync(supplier);

                    created.whenComplete((result, throwable) ->
                            inFlight.remove(key, created)
                    );

                    return created;
                });

        return future.join();
    }

    public int inFlightCount() {
        return inFlight.size();
    }
}
