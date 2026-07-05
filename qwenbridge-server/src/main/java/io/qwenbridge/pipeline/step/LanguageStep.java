package io.qwenbridge.pipeline.step;

import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
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

  @Override
  public LanguageResult execute(ExecutionContext context) {
    return new LanguageResult(languageService.detect(context.request().originalQuery()));
  }
}
