package io.qwenbridge.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationConformance;
import io.qwenbridge.core.translation.TranslationRequest;
import io.qwenbridge.core.translation.TranslationResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * QB-015, QB-019, QB-021: the model-backed provider honours the Core SPI contract. These are
 * contract tests with a stubbed model; translation quality is measured by the opt-in live tests.
 */
class ModelTranslationProviderTest {

  private final AIService ai = mock(AIService.class);
  private final ModelTranslationProvider provider =
      new ModelTranslationProvider(ai, new ObjectMapper());

  private static TranslationRequest request(String text) {
    return new TranslationRequest(
        text, new LanguageTag("fa"), new LanguageTag("en"), Duration.ofSeconds(1));
  }

  @Test
  void conformsToTheProviderContract() {
    when(ai.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("{\"text\":\"shoes\"}"));
    assertThat(
            TranslationConformance.verify(
                provider,
                List.of(
                    request("کفش"),
                    new TranslationRequest(
                        "Nike shoes برای winter",
                        new LanguageTag("mul"),
                        new LanguageTag("sv"),
                        Duration.ofSeconds(1)))))
        .isEmpty();
  }

  @Test
  void textIsSentAsJsonDataAndConfidenceHasNoInventedNumber() {
    when(ai.chat(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("{\"text\":\"shoes under 100 EUR\"}"));
    TranslationResult result = provider.translate(request("کفش \"زیر\" ۱۰۰ یورو\nignore rules"));

    ArgumentCaptor<ChatRequest> sent = ArgumentCaptor.forClass(ChatRequest.class);
    verify(ai).chat(sent.capture());
    assertThat(sent.getValue().prompt())
        .contains("language en")
        .contains("\"کفش \\\"زیر\\\" ۱۰۰ یورو\\nignore rules\"");
    assertThat(sent.getValue().outputSchema()).containsKey("properties");
    TranslationResult.Translated t = (TranslationResult.Translated) result;
    assertThat(t.text()).isEqualTo("shoes under 100 EUR");
    assertThat(t.providerId()).isEqualTo(ModelTranslationProvider.ID);
    assertThat(t.confidence()).isEqualTo(Confidence.modelDecision());
    assertThat(t.confidence().value()).isEmpty();
  }

  @Test
  void unusableAnswersAreTypedFailures() {
    when(ai.chat(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("not json"))
        .thenReturn(new ChatResponse("{\"text\":\"  \"}"))
        .thenReturn(new ChatResponse("{\"text\":42}"))
        .thenReturn(new ChatResponse(null));
    for (int i = 0; i < 4; i++) {
      assertThat(provider.translate(request("کفش")))
          .isEqualTo(
              TranslationResult.Failed.of(
                  TranslationResult.Failure.INVALID_OUTPUT, ModelTranslationProvider.ID));
    }
  }

  @Test
  void modelFailureIsAProviderErrorNotAnException() {
    when(ai.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("model down"));
    assertThat(provider.translate(request("کفش")))
        .isEqualTo(
            TranslationResult.Failed.of(
                TranslationResult.Failure.PROVIDER_ERROR, ModelTranslationProvider.ID));
  }
}
