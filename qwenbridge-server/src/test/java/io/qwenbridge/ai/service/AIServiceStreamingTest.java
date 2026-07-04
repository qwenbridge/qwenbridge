package io.qwenbridge.ai.service;

import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AIServiceStreamingTest {

    private final AIProviderResolver providerResolver = mock(AIProviderResolver.class);
    private final AIProvider provider = mock(AIProvider.class);

    private final AIService aiService = new AIService(providerResolver);

    @Test
    void shouldDelegateStreamingChatToDefaultProvider() {
        StreamingChatRequest request = new StreamingChatRequest("hello");

        List<StreamingChatChunk> chunks = List.of(
                new StreamingChatChunk("hello", false),
                new StreamingChatChunk("", true)
        );

        when(providerResolver.resolveDefault()).thenReturn(provider);
        when(provider.streamChat(any(StreamingChatRequest.class)))
                .thenReturn(Flux.fromIterable(chunks));

        List<StreamingChatChunk> result = aiService.streamChat(request)
                .collectList()
                .block();

        assertThat(result).containsExactlyElementsOf(chunks);

        verify(providerResolver).resolveDefault();
        verify(provider).streamChat(request);
    }
}
