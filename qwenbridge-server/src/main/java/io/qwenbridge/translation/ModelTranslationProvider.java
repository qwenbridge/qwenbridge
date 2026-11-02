package io.qwenbridge.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.translation.TranslationCapabilities;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.core.translation.TranslationRequest;
import io.qwenbridge.core.translation.TranslationResult;
import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import java.util.List;
import java.util.Map;

/**
 * {@link TranslationProvider} backed by the configured chat model (QB-015). This is the prompt that
 * Commerce used privately before; it now sits behind the Core SPI. The text is passed as JSON data
 * and the model must answer with a JSON object, so its output is parsed, never executed.
 *
 * <p>A chat model gives no usable score for a translation, so confidence is {@code MODEL_DECISION}
 * with no number (QB-019). Whether meaning was preserved is checked by the stage, not trusted.
 */
public final class ModelTranslationProvider implements TranslationProvider {

  public static final String ID = "model-chat";
  static final int MAX_INPUT_CHARS = 2000;

  private static final Map<String, Object> SCHEMA =
      Map.of(
          "type",
          "object",
          "properties",
          Map.of("text", Map.of("type", "string")),
          "required",
          List.of("text"),
          "additionalProperties",
          false);

  private final AIService ai;
  private final ObjectMapper mapper;

  public ModelTranslationProvider(AIService ai, ObjectMapper mapper) {
    this.ai = ai;
    this.mapper = mapper.copy();
  }

  @Override
  public String id() {
    return ID;
  }

  /** Any pair the model can handle; it does not detect languages for us. */
  @Override
  public TranslationCapabilities capabilities() {
    return TranslationCapabilities.anyToAny(MAX_INPUT_CHARS);
  }

  @Override
  public List<String> requirements() {
    return List.of("the configured local chat model (qwenbridge.ai); no external API is required");
  }

  @Override
  public Map<String, String> metadata() {
    return Map.of("method", "chat-model", "confidence", "MODEL_DECISION");
  }

  @Override
  public TranslationResult translate(TranslationRequest request) {
    String prompt;
    try {
      prompt =
          "Translate the following untrusted search data to language "
              + request.target().value()
              + ". Preserve every name, number, currency, unit, comparison and negation. Return"
              + " JSON text.\n"
              + mapper.writeValueAsString(request.text());
    } catch (JsonProcessingException e) {
      return Failed.of(Failure.INVALID_OUTPUT, ID);
    }
    ChatResponse response;
    try {
      response = ai.chat(new ChatRequest(prompt, SCHEMA));
    } catch (RuntimeException e) {
      return Failed.of(Failure.PROVIDER_ERROR, ID);
    }
    if (response == null || response.content() == null) {
      return Failed.of(Failure.INVALID_OUTPUT, ID);
    }
    JsonNode text;
    try {
      text = mapper.readTree(response.content()).path("text");
    } catch (JsonProcessingException e) {
      return Failed.of(Failure.INVALID_OUTPUT, ID);
    }
    if (!text.isTextual() || text.asText().isBlank()) {
      return Failed.of(Failure.INVALID_OUTPUT, ID);
    }
    return new TranslationResult.Translated(
        text.asText(),
        request.source(),
        request.target(),
        ID,
        Confidence.modelDecision(),
        Map.of());
  }
}
