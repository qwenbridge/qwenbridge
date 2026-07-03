package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QwenSearchAnalysisServiceStreamingTest {

    private final AIService aiService = mock(AIService.class);
    private final SearchAnalysisPromptBuilder promptBuilder =
            mock(SearchAnalysisPromptBuilder.class);
    private final SearchAnalysisJsonParser parser =
            mock(SearchAnalysisJsonParser.class);
    private final AIAnalysisCache cache = mock(AIAnalysisCache.class);
    private final AIAnalysisCacheKeyBuilder keyBuilder =
            mock(AIAnalysisCacheKeyBuilder.class);
    private final AIAnalysisCacheProperties cacheProperties =
            new AIAnalysisCacheProperties();
    private final AIAnalysisCacheTraceHolder cacheTraceHolder =
            new AIAnalysisCacheTraceHolder();
    private final AIAnalysisSingleFlight singleFlight =
            new AIAnalysisSingleFlight();
    private final AIStreamingEventPublisher streamingEventPublisher =
            mock(AIStreamingEventPublisher.class);

    private final QwenSearchAnalysisService service =
            new QwenSearchAnalysisService(
                    aiService,
                    promptBuilder,
                    parser,
                    cache,
                    keyBuilder,
                    cacheProperties,
                    cacheTraceHolder,
                    singleFlight,
                    streamingEventPublisher
            );

    @Test
    void shouldStreamTokensAndPublishCompletionForRequestAwareAnalysis() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis analysis = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");

        when(aiService.streamChat(any(StreamingChatRequest.class)))
                .thenReturn(Flux.just(
                        new StreamingChatChunk("{\"intent\":", false),
                        new StreamingChatChunk("\"SEARCH\"}", false),
                        new StreamingChatChunk("", true)
                ));

        when(parser.parse("{\"intent\":\"SEARCH\"}", "desk"))
                .thenReturn(analysis);

        SearchAnalysis result = service.analyze("desk", "request-1");

        assertThat(result).isEqualTo(analysis);

        verify(aiService).streamChat(any(StreamingChatRequest.class));
        verify(aiService, never()).chat(any());

        InOrder inOrder = inOrder(streamingEventPublisher);

        inOrder.verify(streamingEventPublisher).token(
                "request-1",
                1L,
                "{\"intent\":"
        );

        inOrder.verify(streamingEventPublisher).token(
                "request-1",
                2L,
                "\"SEARCH\"}"
        );

        inOrder.verify(streamingEventPublisher).completed(
                "request-1",
                2L
        );

        verify(streamingEventPublisher, never()).failed(
                anyString(),
                anyString(),
                anyString()
        );

        verify(cache).put(key, analysis);
    }

    @Test
    void shouldPublishFailureAndReturnFallbackWhenStreamingFails() {
        CacheKey key = new CacheKey("cache-key");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");

        when(aiService.streamChat(any(StreamingChatRequest.class)))
                .thenReturn(Flux.error(new RuntimeException("ollama unavailable")));

        SearchAnalysis result = service.analyze("desk", "request-1");

        assertThat(result.decisionReason())
                .isEqualTo("Fallback keyword search decision.");

        verify(streamingEventPublisher).failed(
                "request-1",
                "AI_STREAM_FAILED",
                "AI streaming analysis failed"
        );

        verify(streamingEventPublisher, never()).completed(
                anyString(),
                anyLong()
        );

        verify(cache).put(eq(key), any(SearchAnalysis.class));
    }

    @Test
    void shouldKeepLegacyNonStreamingBehaviorWithoutRequestId() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis analysis = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");

        when(aiService.chat(any()))
                .thenReturn(new io.qwenbridge.ai.contract.ChatResponse(
                        "{\"intent\":\"SEARCH\"}"
                ));

        when(parser.parse("{\"intent\":\"SEARCH\"}", "desk"))
                .thenReturn(analysis);

        SearchAnalysis result = service.analyze("desk");

        assertThat(result).isEqualTo(analysis);

        verify(aiService).chat(any());
        verify(aiService, never()).streamChat(any());

        verifyNoInteractions(streamingEventPublisher);
    }
}
