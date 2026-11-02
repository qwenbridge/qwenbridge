package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.confidence.ConfidenceAssessment;
import io.qwenbridge.confidence.ConfidenceService;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
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

    SearchAnalysis analysis = context.get(SearchAnalysis.class);
    ConfidenceAssessment assessment = confidenceService.assess(analysis);
    context.store(ConfidenceAssessment.class, assessment);
    return new ConfidenceResult(assessment.value());
  }
}
