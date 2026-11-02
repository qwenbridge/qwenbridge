package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.language.LanguageService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class LanguageStep implements PipelineStep<LanguageResult> {

  private final LanguageService languageService;

  @Override
  public PipelineStage stage() {
    return PipelineStage.LANGUAGE;
  }

  @Override
  public String name() {
    return "LanguageStep";
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public Class<LanguageResult> resultType() {
    return LanguageResult.class;
  }

  /**
   * Detects language on the original text. A declared language is recorded but never overrides a
   * detection (ADR 0013); it is used only when nothing could be detected.
   */
  @Override
  public LanguageResult execute(ExecutionContext context) {
    Optional<LanguageTag> declared =
        context.request().input().declaredLanguageOptional().flatMap(LanguageTag::parse);
    LanguageAnalysis analysis =
        languageService.analyze(context.request().originalQuery(), declared);
    String language =
        analysis.primaryDetermined()
            ? analysis.primaryLanguage()
            : declared.map(LanguageTag::primaryLanguage).orElse(LanguageAnalysis.UNKNOWN);
    return new LanguageResult(language, analysis);
  }
}
