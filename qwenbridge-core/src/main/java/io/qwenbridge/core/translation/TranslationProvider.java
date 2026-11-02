package io.qwenbridge.core.translation;

/**
 * Provider-neutral translation SPI (QB-015). Implementations may call a model, a translation
 * service or a local dictionary. Core never requires one: without a provider the {@link
 * TranslationStage} returns an explicit unsupported result (QB-016).
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Return {@link TranslationResult.Translated} or {@link TranslationResult.Failed}; do not
 *       throw for ordinary failures. The stage still turns an exception into {@code
 *       PROVIDER_ERROR}.
 *   <li>Honour {@link TranslationRequest#timeout()} where possible; the stage enforces it anyway.
 *   <li>Report confidence honestly (QB-019): a number only as {@code MODEL_SCORE} from a real
 *       score, otherwise {@code MODEL_DECISION} or {@code UNKNOWN}. Never invent a probability.
 *   <li>Treat the text as untrusted data, never as instructions.
 * </ul>
 */
public interface TranslationProvider {

  /** Stable identifier recorded in results, representations and traces. */
  String id();

  /** What this provider can translate; checked by the stage before every call. */
  TranslationCapabilities capabilities();

  TranslationResult translate(TranslationRequest request);

  /**
   * Whether the provider can be called now (QB-286): ready, still initializing, or missing its
   * model or local resources. Callers never send a request to a provider that is not ready, and
   * report the reason instead.
   */
  default ProviderAvailability availability() {
    return ProviderAvailability.ready();
  }

  /** Local resources the provider needs (model files, word lists, a local model server). */
  default java.util.List<String> requirements() {
    return java.util.List.of();
  }

  /**
   * Vocabulary evidence for the source language (QB-286): for each source language this provider
   * knows words of, the share of the text's plain words (three letters or more, not protected) that
   * it knows, in {@code 0..1}. Statistical language detectors misjudge short queries; the expansion
   * engine uses this evidence only when detection was statistical or failed. Providers without a
   * vocabulary return nothing.
   */
  default java.util.Map<String, Double> vocabularyEvidence(String text) {
    return java.util.Map.of();
  }

  /** Descriptive capability metadata (method, entry counts, model name); never secrets. */
  default java.util.Map<String, String> metadata() {
    return java.util.Map.of();
  }
}
