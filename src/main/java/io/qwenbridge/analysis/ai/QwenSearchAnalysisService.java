package io.qwenbridge.analysis.ai;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.AIAnalysisCacheKeyBuilder;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.cache.coalescing.AIAnalysisSingleFlight;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTraceHolder;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.parser.SearchAnalysisJsonParser;
import io.qwenbridge.analysis.prompt.SearchAnalysisPromptBuilder;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.streaming.ai.AIStreamingEventPublisher;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class QwenSearchAnalysisService implements SearchAnalysisService {

    private final AIService aiService;
    private final SearchAnalysisPromptBuilder promptBuilder;
    private final SearchAnalysisJsonParser parser;
    private final AIAnalysisCache cache;
    private final AIAnalysisCacheKeyBuilder cacheKeyBuilder;
    private final AIAnalysisCacheProperties cacheProperties;
    private final AIAnalysisCacheTraceHolder cacheTraceHolder;
    private final AIAnalysisSingleFlight singleFlight;
    private final AIStreamingEventPublisher streamingEventPublisher;
    private final StreamingSessionRegistry streamingSessionRegistry;

    @Override
    public SearchAnalysis analyze(String query) {
        return analyzeInternal(query, null);
    }

    @Override
    public SearchAnalysis analyze(String query, String requestId) {
        return analyzeInternal(query, requestId);
    }

    private SearchAnalysis analyzeInternal(String query, String requestId) {
        CacheKey cacheKey = cacheKeyBuilder.build(query);
        cacheTraceHolder.set(AIAnalysisCacheTrace.miss(
                cacheKey.value(),
                cacheProperties.provider(),
                cacheProperties.model(),
                cacheProperties.version()
        ));

        try {
            var cached = cache.get(cacheKey);

            if (cached.isPresent()) {
                cacheTraceHolder.set(AIAnalysisCacheTrace.hit(
                        cacheKey.value(),
                        cacheProperties.provider(),
                        cacheProperties.model(),
                        cacheProperties.version()
                ));
                return cached.get();
            }
        } catch (Exception ignored) {
            // Cache failures must never break AI analysis.
        }

        return singleFlight.execute(cacheKey, () -> {
            SearchAnalysis analysis = analyzeWithAI(query, requestId);

            if (!streamingSessionRegistry.isRequestCancelled(requestId)) {
                try {
                    cache.put(cacheKey, analysis);
                } catch (Exception ignored) {
                    // Cache failures must never break AI analysis.
                }
            }

            return analysis;
        });
    }

    private SearchAnalysis analyzeWithAI(String query, String requestId) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> analyzeWithAIBlocking(query, requestId))
                    .orTimeout(cacheProperties.analysisTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(ignored -> {
                        streamingEventPublisher.failed(
                                requestId,
                                "AI_ANALYSIS_TIMEOUT",
                                "AI analysis did not complete within the configured timeout"
                        );
                        return SearchAnalysis.fallback(query);
                    })
                    .join();
        } catch (Exception ignored) {
            streamingEventPublisher.failed(
                    requestId,
                    "AI_ANALYSIS_FAILED",
                    "AI analysis failed"
            );
            return SearchAnalysis.fallback(query);
        }
    }

    private SearchAnalysis analyzeWithAIBlocking(String query, String requestId) {
        String prompt = promptBuilder.build(query);

        if (requestId == null || requestId.isBlank()) {
            String content = aiService.chat(new ChatRequest(prompt)).content();
            return parser.parse(content, query);
        }

        AtomicLong tokenIndex = new AtomicLong(0L);
        StringBuilder content = new StringBuilder();

        try {
            aiService.streamChat(new StreamingChatRequest(prompt))
                    .takeWhile(chunk -> !streamingSessionRegistry.isRequestCancelled(requestId))
                    .doOnNext(chunk -> {
                        if (!streamingSessionRegistry.isRequestCancelled(requestId)
                                && chunk.content() != null
                                && !chunk.content().isBlank()) {
                            long index = tokenIndex.incrementAndGet();
                            content.append(chunk.content());
                            streamingEventPublisher.token(
                                    requestId,
                                    index,
                                    chunk.content()
                            );
                        }
                    })
                    .blockLast(cacheProperties.analysisTimeout());

            if (streamingSessionRegistry.isRequestCancelled(requestId)) {
                return SearchAnalysis.fallback(query);
            }

            streamingEventPublisher.completed(requestId, tokenIndex.get());

            return parser.parse(content.toString(), query);
        } catch (Exception exception) {
            streamingEventPublisher.failed(
                    requestId,
                    "AI_STREAM_FAILED",
                    "AI streaming analysis failed"
            );
            return SearchAnalysis.fallback(query);
        }
    }
}
