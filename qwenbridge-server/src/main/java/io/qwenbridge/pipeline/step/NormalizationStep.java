package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.text.SearchTextNormalizer;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.service.InputNormalizer;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.spelling.TypoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Produces two derived representations of the original query (QB-009–QB-012): the
 * meaning-preserving search form and, via the existing rule chain, the threat canonical form. The
 * language step runs first, so language-specific search normalization can use its result.
 */
@ApplicationScoped
public class NormalizationStep implements PipelineStep<NormalizedInput> {

  private final InputNormalizer inputNormalizer;
  private final TypoService typo;

  /** Without typo correction (unit tests). */
  public NormalizationStep(InputNormalizer inputNormalizer) {
    this(inputNormalizer, null);
  }

  @Inject
  public NormalizationStep(InputNormalizer inputNormalizer, TypoService typo) {
    this.inputNormalizer = inputNormalizer;
    this.typo = typo;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.NORMALIZATION;
  }

  @Override
  public String name() {
    return "NormalizationStep";
  }

  @Override
  public int order() {
    return 15;
  }

  @Override
  public Class<NormalizedInput> resultType() {
    return NormalizedInput.class;
  }

  @Override
  public NormalizedInput execute(ExecutionContext context) {
    String original = context.request().originalQuery();
    NormalizedInput threat = inputNormalizer.normalize(original);
    SearchTextNormalizer.Result search =
        SearchTextNormalizer.normalize(original, normalizationLanguage(context));
    String searchForm = search.normalized();
    if (typo != null) {
      // QB-291: typo correction on the search form only; the original and the threat canonical
      // form are untouched, and the QueryIR keeps extracting from the original text.
      LanguageResult language = context.get(LanguageResult.class);
      var correction =
          typo.correct(null, original, searchForm, language == null ? null : language.analysis());
      context.store(io.qwenbridge.core.spelling.CorrectionResult.class, correction);
      if (correction.mode().applies() && correction.changed()) searchForm = correction.corrected();
    }
    return new NormalizedInput(
        original,
        searchForm,
        threat.trace(),
        threat.normalizedQuery(),
        search.steps().stream()
            .filter(SearchTextNormalizer.Step::changed)
            .map(SearchTextNormalizer.Step::name)
            .toList());
  }

  private static Optional<LanguageTag> normalizationLanguage(ExecutionContext context) {
    LanguageResult language = context.get(LanguageResult.class);
    if (language != null && language.analysis() != null) {
      return language.analysis().normalizationLanguage();
    }
    if (language != null && !LanguageAnalysis.UNKNOWN.equals(language.language())) {
      return LanguageTag.parse(language.language());
    }
    return Optional.empty();
  }
}
