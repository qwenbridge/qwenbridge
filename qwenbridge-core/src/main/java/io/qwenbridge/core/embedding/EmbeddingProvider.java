package io.qwenbridge.core.embedding;

import java.time.Duration;
import java.util.List;

/**
 * Provider-neutral embedding SPI (QB-110), separate from chat. Implementations return one vector
 * per text in {@link #model()}'s space, or a typed failure.
 */
public interface EmbeddingProvider {

  String id();

  EmbeddingModel model();

  EmbeddingResult embed(List<String> texts, Duration timeout);
}
