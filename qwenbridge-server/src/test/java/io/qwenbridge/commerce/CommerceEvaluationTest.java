package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommerceEvaluationTest {
  @Test
  void referenceMetricsAndCalibrationAreMeasuredIndependentlyOfModelClaims() {
    var report =
        CommerceEvaluation.retrieval(
            List.of(
                new CommerceEvaluation.Judgment("q", List.of("bad", "good"), Map.of("good", 3))),
            2);
    assertThat(report.precisionAtK()).isEqualTo(.5);
    assertThat(report.recallAtK()).isEqualTo(1);
    assertThat(report.mrr()).isEqualTo(.5);
    assertThat(report.ndcgAtK()).isBetween(.63, .64);
    var calibration =
        CommerceEvaluation.calibration(
            List.of(
                new CommerceEvaluation.Observation(.9, true),
                new CommerceEvaluation.Observation(.2, false)));
    assertThat(calibration.brierScore()).isCloseTo(.025, within(.000001));
    assertThat(calibration.expectedCalibrationError()).isCloseTo(.15, within(.000001));
    assertThatThrownBy(() -> CommerceEvaluation.retrieval(List.of(), 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new CommerceEvaluation.Judgment("q", List.of("same", "same"), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
