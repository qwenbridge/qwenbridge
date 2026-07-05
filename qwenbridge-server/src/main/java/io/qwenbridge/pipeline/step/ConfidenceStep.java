package io.qwenbridge.pipeline.step;

import io.qwenbridge.confidence.ConfidenceService;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfidenceStep implements PipelineStep<ConfidenceResult> {

  private final ConfidenceService confidenceService;

  @Override
  public PipelineStage stage() {
    return PipelineStage.PIPELINE;
  }

  @Override
  public String name() {
    return "ConfidenceStep";
  }

  @Override
  public int order() {
    return 90;
  }

  @Override
  public Class<ConfidenceResult> resultType() {
    return ConfidenceResult.class;
  }

  @Override
  public ConfidenceResult execute(ExecutionContext context) {

    return new ConfidenceResult(
        confidenceService.calculate(
            context.request().originalQuery(), context.get(RewriteResult.class).rewrites()));
  }
}
