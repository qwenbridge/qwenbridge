package io.qwenbridge.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.translation.TranslationStage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds the translation stages (QB-016). {@link #configured()} is the optional stage of the v1
 * pipeline; it has a provider only when {@code qwenbridge.translation.provider=model}, so the
 * server starts and answers without any translation service. {@link #model()} always uses the chat
 * model; Commerce search uses it because Commerce already requires the model for analysis. An
 * unknown provider name is rejected rather than silently disabling translation.
 */
@Singleton
public final class TranslationStages {

  private final TranslationStage configured;
  private final TranslationStage model;

  @Inject
  public TranslationStages(
      AIService ai,
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.translation.provider", defaultValue = "none")
          String provider,
      @ConfigProperty(name = "qwenbridge.translation.timeout", defaultValue = "30s")
          Duration timeout) {
    ModelTranslationProvider modelProvider = new ModelTranslationProvider(ai, mapper);
    this.model = new TranslationStage(Optional.of(modelProvider), timeout);
    String kind = provider == null ? "none" : provider.trim().toLowerCase(Locale.ROOT);
    this.configured =
        switch (kind) {
          case "model" -> model;
          case "none", "" -> new TranslationStage(Optional.empty(), timeout);
          default ->
              throw new IllegalArgumentException(
                  "qwenbridge.translation.provider must be none or model");
        };
  }

  public TranslationStage configured() {
    return configured;
  }

  public TranslationStage model() {
    return model;
  }
}
