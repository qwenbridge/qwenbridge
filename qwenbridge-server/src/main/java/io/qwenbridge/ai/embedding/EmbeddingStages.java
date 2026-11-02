package io.qwenbridge.ai.embedding;

import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The server's embedding stage (QB-110, QB-111). {@code qwenbridge.embedding.enabled=false} runs
 * without any embedding provider; vector retrieval then reports itself unavailable instead of
 * pretending lexical results are semantic (QB-115).
 */
@Singleton
public final class EmbeddingStages {

  private final EmbeddingStage stage;

  @Inject
  public EmbeddingStages(
      AIService ai,
      @ConfigProperty(name = "qwenbridge.embedding.enabled", defaultValue = "true") boolean enabled,
      @ConfigProperty(name = "qwenbridge.ai.ollama.embedding-model", defaultValue = "bge-m3")
          String modelName,
      @ConfigProperty(name = "qwenbridge.embedding.model-version", defaultValue = "1")
          String modelVersion,
      @ConfigProperty(name = "qwenbridge.embedding.dimensions", defaultValue = "1024")
          int dimensions,
      @ConfigProperty(name = "qwenbridge.embedding.timeout", defaultValue = "30s")
          Duration timeout) {
    EmbeddingModel model =
        new EmbeddingModel(new EmbeddingSpace(modelName, modelVersion, dimensions), 32, 8000);
    this.stage =
        new EmbeddingStage(
            enabled ? Optional.of(new AIServiceEmbeddingProvider(ai, model)) : Optional.empty(),
            timeout);
  }

  public EmbeddingStage stage() {
    return stage;
  }
}
