package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.step.ModelSafetyStep;
import io.qwenbridge.threat.ThreatResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelSafetyStepTest {
  @Test
  void modelCannotUndoRulesAndUnavailableAnalysisCannotExecuteUncheckedFilters() {
    var step = new ModelSafetyStep();
    var context = new ExecutionContext("shoes");
    context.store(ThreatResult.class, new ThreatResult(false, List.of("rule-block")));
    context.store(
        SearchAnalysis.class,
        SearchAnalysis.builder().semanticValidated(true).modelThreatDecision("ALLOW").build());
    assertThat(step.execute(context).safe()).isFalse();
    context.store(ThreatResult.class, ThreatResult.noThreat());
    for (String decision : List.of("BLOCK", "REVIEW")) {
      context.store(
          SearchAnalysis.class,
          SearchAnalysis.builder().semanticValidated(true).modelThreatDecision(decision).build());
      assertThat(step.execute(context).safe()).isFalse();
    }
    context.store(SearchAnalysis.class, SearchAnalysis.fallback("shoes"));
    assertThat(step.execute(context).safe()).isFalse();
  }
}
