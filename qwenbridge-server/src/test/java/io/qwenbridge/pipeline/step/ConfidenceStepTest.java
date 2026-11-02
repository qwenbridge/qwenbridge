package io.qwenbridge.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.confidence.ConfidenceAssessment;
import io.qwenbridge.confidence.ConfidenceService;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfidenceStepTest {

  @Test
  void shouldNotInferConfidenceFromMultipleRewrites() {
    ExecutionContext context = new ExecutionContext("میز");
    context.store(RewriteResult.class, new RewriteResult(true, "mock", List.of("desk", "table")));

    ConfidenceStep step = new ConfidenceStep(new ConfidenceService());

    ConfidenceResult result = step.execute(context);

    assertThat(result.value()).isZero();
    assertThat(context.get(ConfidenceAssessment.class).method()).isEqualTo("unavailable");
  }

  @Test
  void shouldNotInferConfidenceFromSingleRewrite() {
    ExecutionContext context = new ExecutionContext("desk");
    context.store(RewriteResult.class, new RewriteResult(true, "mock", List.of("desk")));

    ConfidenceStep step = new ConfidenceStep(new ConfidenceService());

    ConfidenceResult result = step.execute(context);

    assertThat(result.value()).isZero();
  }

  @Test
  void shouldUseModelSignalsWhenAnalysisIsAvailable() {
    ExecutionContext context = new ExecutionContext("desk");
    context.store(
        SearchAnalysis.class,
        SearchAnalysis.builder()
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(.9)
            .semanticValidated(true)
            .semanticScore(.8)
            .decisionConfidence(.3)
            .build());
    ConfidenceResult result = new ConfidenceStep(new ConfidenceService()).execute(context);
    assertThat(result.value()).isEqualTo(.3);
    assertThat(context.get(ConfidenceAssessment.class).calibrated()).isFalse();
  }
}
