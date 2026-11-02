package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.text.SearchTextNormalizer;
import io.qwenbridge.core.translation.TextRepresentations;
import io.qwenbridge.core.translation.TranslationOutcome;
import io.qwenbridge.core.translation.TranslationStage;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.RequestHints;
import io.qwenbridge.pipeline.result.TranslationStepResult;
import io.qwenbridge.translation.TranslationStages;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * The optional translation stage of the v1 pipeline (QB-015–QB-020). It runs only when the client
 * names the content language with the {@code content_language} context entry and that language
 * differs from the query's. The search-normalized text is translated; the original is never
 * replaced. A changed translation is rejected (QB-018), and without a configured provider the
 * result is an explicit {@code NO_PROVIDER} (QB-016).
 *
 * <p>It runs after the deterministic threat check and the model safety step (order 27), so a
 * blocked query is never sent to a translation provider.
 *
 * <p>Informational in v1, like the QueryIR: the legacy pipeline still searches with its own
 * analysis. Section 2 retrieval uses the accepted translation for cross-language matching.
 */
@ApplicationScoped
public class TranslationStep implements PipelineStep<TranslationStepResult> {

  /** Request context key naming the language of the content to search. */
  public static final String CONTENT_LANGUAGE = "content_language";

  private final TranslationStages stages;

  @Inject
  public TranslationStep(TranslationStages stages) {
    this.stages = stages;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.NORMALIZATION;
  }

  @Override
  public String name() {
    return "TranslationStep";
  }

  @Override
  public int order() {
    return 27;
  }

  @Override
  public boolean publishEvents() {
    return false;
  }

  @Override
  public Class<TranslationStepResult> resultType() {
    return TranslationStepResult.class;
  }

  @Override
  public TranslationStepResult execute(ExecutionContext context) {
    String original = context.request().originalQuery();
    TextRepresentations text = TextRepresentations.of(original);
    NormalizedInput normalized = context.get(NormalizedInput.class);
    if (normalized != null) {
      text =
          text.withNormalized(
              normalized.normalizedQuery(), SearchTextNormalizer.VERSION, normalized.searchSteps());
    }
    Optional<LanguageTag> target = contentLanguage(context);
    LanguageTag source = source(context.get(LanguageResult.class));
    TranslationStage stage = stages.configured();
    TranslationOutcome outcome =
        stage.translate(
            text.translationInput(),
            source,
            target,
            EntityDictionary.EMPTY,
            TranslationStage.Policy.ENFORCE);
    return new TranslationStepResult(text.withTranslation(outcome), outcome, source, target);
  }

  static Optional<LanguageTag> contentLanguage(ExecutionContext context) {
    RequestHints hints = context.get(RequestHints.class);
    if (hints == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(hints.context().get(CONTENT_LANGUAGE)).flatMap(LanguageTag::parse);
  }

  /** Code-switched text is {@code mul}; undetermined text stays {@code und} (never guessed). */
  static LanguageTag source(LanguageResult language) {
    if (language == null || language.analysis() == null) {
      return LanguageTag.UNDETERMINED;
    }
    LanguageAnalysis analysis = language.analysis();
    if (analysis.mixed()) {
      return new LanguageTag("mul");
    }
    if (analysis.primaryDetermined()) {
      return LanguageTag.parse(analysis.primaryLanguage()).orElse(LanguageTag.UNDETERMINED);
    }
    return analysis.declared().orElse(LanguageTag.UNDETERMINED);
  }
}
