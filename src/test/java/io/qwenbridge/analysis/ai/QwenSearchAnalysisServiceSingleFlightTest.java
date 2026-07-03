package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.AIAnalysisCacheKeyBuilder;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.coalescing.AIAnalysisSingleFlight;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTraceHolder;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.parser.SearchAnalysisJsonParser;
import io.qwenbridge.analysis.prompt.SearchAnalysisPromptBuilder;
import io.qwenbridge.streaming.ai.AIStreamingEventPublisher;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QwenSearchAnalysisServiceSingleFlightTest {

    @Test
    void shouldCoalesceConcurrentCacheMissesIntoSingleAICall() throws Exception {
        AIService aiService = mock(AIService.class);
        SearchAnalysisPromptBuilder promptBuilder = mock(SearchAnalysisPromptBuilder.class);
        SearchAnalysisJsonParser parser = mock(SearchAnalysisJsonParser.class);
        AIAnalysisCache cache = mock(AIAnalysisCache.class);
        AIAnalysisCacheKeyBuilder keyBuilder = mock(AIAnalysisCacheKeyBuilder.class);
        AIAnalysisCacheProperties cacheProperties = new AIAnalysisCacheProperties();
        AIAnalysisCacheTraceHolder cacheTraceHolder = new AIAnalysisCacheTraceHolder();
        AIAnalysisSingleFlight singleFlight = new AIAnalysisSingleFlight();
        AIStreamingEventPublisher streamingEventPublisher = mock(AIStreamingEventPublisher.class);
        StreamingSessionRegistry streamingSessionRegistry = mock(StreamingSessionRegistry.class);

        QwenSearchAnalysisService service = new QwenSearchAnalysisService(
                aiService,
                promptBuilder,
                parser,
                cache,
                keyBuilder,
                cacheProperties,
                cacheTraceHolder,
                singleFlight,
                streamingEventPublisher,
                streamingSessionRegistry
        );

        CacheKey key = new CacheKey("same-key");
        SearchAnalysis analysis = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");
        when(aiService.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(150);
            return new ChatResponse("content");
        });
        when(parser.parse("content", "desk")).thenReturn(analysis);

        int requestCount = 100;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            var futures = new ArrayList<java.util.concurrent.Future<SearchAnalysis>>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return service.analyze("desk");
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            for (var future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(analysis);
            }
        }

        verify(aiService, times(1)).chat(any(ChatRequest.class));
        verify(cache, times(1)).put(key, analysis);
        assertThat(singleFlight.inFlightCount()).isZero();
    }
}
