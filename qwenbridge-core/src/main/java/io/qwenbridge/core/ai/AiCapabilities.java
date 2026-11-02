package io.qwenbridge.core.ai;

import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.IndexCompatibility;
import io.qwenbridge.core.ranking.Reranker;
import java.util.Optional;

/**
 * Capability checks done before planning or execution (QB-246): what a call needs that a provider
 * does not offer is reported as a reason, and the provider is not called.
 */
public final class AiCapabilities {

  private AiCapabilities() {}

  public static Optional<String> unmet(ChatCapabilities capabilities, ChatCall call) {
    if (call.streaming() && !capabilities.streaming()) return Optional.of("streaming");
    if (call.outputSchema().isPresent() && !capabilities.structuredOutput())
      return Optional.of("structured output");
    if (call.prompt().length() > capabilities.maxInputChars()) return Optional.of("input length");
    if (call.maxOutputTokens() > capabilities.maxOutputTokens())
      return Optional.of("output tokens");
    return Optional.empty();
  }

  /** An embedding model can serve an index only in the index's own space and within its limits. */
  public static Optional<String> unmet(EmbeddingModel model, EmbeddingSpace index, int batch) {
    var compatibility = IndexCompatibility.check(index, model.space());
    if (!compatibility.compatible())
      return Optional.of(
          "embedding space (reindex required: " + compatibility.reindexReasons() + ")");
    if (batch > model.maxBatch()) return Optional.of("batch size");
    return Optional.empty();
  }

  public static Optional<String> unmet(Reranker reranker, int candidates) {
    return candidates > reranker.maxCandidates()
        ? Optional.of("candidates (at most " + reranker.maxCandidates() + ")")
        : Optional.empty();
  }
}
