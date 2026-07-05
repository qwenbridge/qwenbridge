package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.SemanticResult;
import org.springframework.stereotype.Component;

@Component
public class SemanticStep implements PipelineStep<SemanticResult> {

  @Override
  public PipelineStage stage() {
    return PipelineStage.SEMANTIC;
  }

  @Override
  public String name() {
    return "SemanticStep";
  }

  @Override
  public int order() {
    return 50;
  }

  @Override
  public Class<SemanticResult> resultType() {
    return SemanticResult.class;
  }

  @Override
  public SemanticResult execute(ExecutionContext context) {
    SearchAnalysis analysis = context.get(SearchAnalysis.class);

    if (analysis == null) {
      return SemanticResult.notValidated();
    }

    return analysis.toSemanticResult(context.request().originalQuery());
  }
}
