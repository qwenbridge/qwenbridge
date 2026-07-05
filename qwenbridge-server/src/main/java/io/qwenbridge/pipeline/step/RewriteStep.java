package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.springframework.stereotype.Component;

@Component
public class RewriteStep implements PipelineStep<RewriteResult> {

  @Override
  public PipelineStage stage() {
    return PipelineStage.REWRITE;
  }

  @Override
  public String name() {
    return "RewriteStep";
  }

  @Override
  public int order() {
    return 40;
  }

  @Override
  public Class<RewriteResult> resultType() {
    return RewriteResult.class;
  }

  @Override
  public RewriteResult execute(ExecutionContext context) {
    SearchAnalysis analysis = context.get(SearchAnalysis.class);

    if (analysis == null) {
      return RewriteResult.none();
    }

    return analysis.toRewriteResult();
  }
}
