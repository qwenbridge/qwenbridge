package io.qwenbridge.ai.embedding;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The configured AI provider's embedding endpoint behind the Core {@link EmbeddingProvider} SPI
 * (QB-110), separate from chat. The model metadata (name, version, dimensions) is configuration:
 * the stage rejects any vector whose length does not match, so a changed model is detected instead
 * of silently mixing spaces (QB-111).
 */
public final class AIServiceEmbeddingProvider implements EmbeddingProvider {

  public static final String ID = "ai-service-embedding";

  private final AIService ai;
  private final EmbeddingModel model;

  public AIServiceEmbeddingProvider(AIService ai, EmbeddingModel model) {
    this.ai = ai;
    this.model = model;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public EmbeddingModel model() {
    return model;
  }

  @Override
  public EmbeddingResult embed(List<String> texts, Duration timeout) {
    List<double[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      EmbeddingResponse response;
      try {
        response = ai.embed(new EmbeddingRequest(text));
      } catch (RuntimeException e) {
        return new EmbeddingResult.Failed(EmbeddingResult.Failure.UNAVAILABLE, ID);
      }
      if (response == null || response.vector().isEmpty()) {
        return new EmbeddingResult.Failed(EmbeddingResult.Failure.INVALID_OUTPUT, ID);
      }
      double[] v = new double[response.vector().size()];
      for (int i = 0; i < v.length; i++) {
        Double x = response.vector().get(i);
        v[i] = x == null ? Double.NaN : x;
      }
      vectors.add(v);
    }
    return new EmbeddingResult.Embedded(vectors, model.space());
  }
}
