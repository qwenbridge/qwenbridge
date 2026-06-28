package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.AIAnalysisCacheKeyBuilder;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.parser.SearchAnalysisJsonParser;
import io.qwenbridge.analysis.prompt.SearchAnalysisPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QwenSearchAnalysisServiceCacheTest {

    private final AIService aiService = mock(AIService.class);
    private final SearchAnalysisPromptBuilder promptBuilder = mock(SearchAnalysisPromptBuilder.class);
    private final SearchAnalysisJsonParser parser = mock(SearchAnalysisJsonParser.class);
    private final AIAnalysisCache cache = mock(AIAnalysisCache.class);
    private final AIAnalysisCacheKeyBuilder keyBuilder = mock(AIAnalysisCacheKeyBuilder.class);

    private final QwenSearchAnalysisService service =
            new QwenSearchAnalysisService(
                    aiService,
                    promptBuilder,
                    parser,
                    cache,
                    keyBuilder
            );

    @Test
    void shouldReturnCachedAnalysisWhenCacheHits() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis cached = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.of(cached));

        SearchAnalysis result = service.analyze("desk");

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(aiService);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void shouldCallAIAndStoreResultWhenCacheMisses() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis parsed = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");
        when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("content"));
        when(parser.parse("content", "desk")).thenReturn(parsed);

        SearchAnalysis result = service.analyze("desk");

        assertThat(result).isEqualTo(parsed);
        verify(aiService).chat(any(ChatRequest.class));
        verify(cache).put(key, parsed);
    }

    @Test
    void shouldContinueWithAIWhenCacheReadFails() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis parsed = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenThrow(new RuntimeException("redis down"));
        when(promptBuilder.build("desk")).thenReturn("prompt");
        when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("content"));
        when(parser.parse("content", "desk")).thenReturn(parsed);

        SearchAnalysis result = service.analyze("desk");

        assertThat(result).isEqualTo(parsed);
        verify(aiService).chat(any(ChatRequest.class));
        verify(cache).put(key, parsed);
    }

    @Test
    void shouldReturnFallbackWhenAIAndCacheFail() {
        CacheKey key = new CacheKey("cache-key");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenThrow(new RuntimeException("redis down"));
        when(promptBuilder.build("desk")).thenReturn("prompt");
        when(aiService.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("ai down"));

        SearchAnalysis result = service.analyze("desk");

        assertThat(result.decisionReason()).isEqualTo("Fallback keyword search decision.");
        verify(cache).put(eq(key), any(SearchAnalysis.class));
    }

    @Test
    void shouldIgnoreCacheWriteFailures() {
        CacheKey key = new CacheKey("cache-key");
        SearchAnalysis parsed = SearchAnalysis.fallback("desk");

        when(keyBuilder.build("desk")).thenReturn(key);
        when(cache.get(key)).thenReturn(Optional.empty());
        when(promptBuilder.build("desk")).thenReturn("prompt");
        when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("content"));
        when(parser.parse("content", "desk")).thenReturn(parsed);
        doThrow(new RuntimeException("redis down")).when(cache).put(key, parsed);

        SearchAnalysis result = service.analyze("desk");

        assertThat(result).isEqualTo(parsed);
        verify(aiService).chat(any(ChatRequest.class));
    }
}
