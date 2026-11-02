package io.qwenbridge.ai.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.provider.ollama.client.OllamaClient;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import io.qwenbridge.testsupport.MutinyUnitTestExtension;
import io.smallrye.mutiny.Multi;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(MutinyUnitTestExtension.class)
class OllamaProviderOutputFormatTest {
  private final OllamaClient client = mock(OllamaClient.class);
  private final OllamaProvider provider =
      new OllamaProvider(client, new OllamaProperties(null, null, null, null, null, 0, true));

  @Test
  void ordinaryChatDoesNotAcquireJsonRestrictions() throws Exception {
    when(client.chat(any()))
        .thenReturn(
            new OllamaChatResponse(
                "qwen2.5", new OllamaChatResponse.Message("assistant", "Hello"), true));
    assertThat(provider.chat(new ChatRequest("hello")).content()).isEqualTo("Hello");
    var capture = ArgumentCaptor.forClass(OllamaChatRequest.class);
    verify(client).chat(capture.capture());
    String payload = new ObjectMapper().writeValueAsString(capture.getValue());
    assertThat(payload).doesNotContain("\"format\"", "\"options\"");
  }

  @Test
  void streamingAnalysisCarriesSchemaAndDeterministicSampling() {
    Map<String, Object> schema = Map.of("type", "object");
    when(client.streamChat(any()))
        .thenReturn(
            Multi.createFrom()
                .item(
                    new OllamaStreamingChatResponse(
                        "qwen2.5", new OllamaChatResponse.Message("assistant", "{}"), true)));
    provider
        .streamChat(new StreamingChatRequest("analyze", schema))
        .collect()
        .asList()
        .await()
        .indefinitely();
    var capture = ArgumentCaptor.forClass(OllamaChatRequest.class);
    verify(client).streamChat(capture.capture());
    assertThat(capture.getValue().format()).isEqualTo(schema);
    assertThat(capture.getValue().options()).containsEntry("temperature", 0);
    assertThat(capture.getValue().stream()).isTrue();
  }
}
