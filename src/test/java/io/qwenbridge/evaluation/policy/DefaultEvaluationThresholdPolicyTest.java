package io.qwenbridge.evaluation.policy;

import io.qwenbridge.evaluation.model.EvaluationGateResult;
import io.qwenbridge.evaluation.model.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEvaluationThresholdPolicyTest {

    private final DefaultEvaluationThresholdPolicy policy =
            new DefaultEvaluationThresholdPolicy();

    @Test
    void shouldPassWhenAllMetricsMeetThresholds() {
        EvaluationResult result = new EvaluationResult(
                10,
                0.70,
                0.80,
                0.75,
                0.90
        );

        EvaluationGateResult gate = policy.evaluate(result);

        assertThat(gate.passed()).isTrue();
        assertThat(gate.violations()).isEmpty();
    }

    @Test
    void shouldFailWhenAnyMetricIsBelowThreshold() {
        EvaluationResult result = new EvaluationResult(
                10,
                0.59,
                0.80,
                0.75,
                0.90
        );

        EvaluationGateResult gate = policy.evaluate(result);

        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations())
                .containsExactly("precisionAtK 0.5900 is below required minimum 0.6000");
    }

    @Test
    void shouldFailWithAllViolationsWhenMultipleMetricsAreBelowThreshold() {
        EvaluationResult result = new EvaluationResult(
                10,
                0.10,
                0.20,
                0.30,
                0.40
        );

        EvaluationGateResult gate = policy.evaluate(result);

        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations()).containsExactly(
                "precisionAtK 0.1000 is below required minimum 0.6000",
                "recallAtK 0.2000 is below required minimum 0.6000",
                "meanReciprocalRank 0.3000 is below required minimum 0.6000",
                "ndcgAtK 0.4000 is below required minimum 0.7000"
        );
    }

    @Test
    void shouldFailWhenEvaluationHasNoQueries() {
        EvaluationGateResult gate = policy.evaluate(
                new EvaluationResult(0, 1.0, 1.0, 1.0, 1.0)
        );

        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations())
                .containsExactly("evaluation result must contain at least one query");
    }

    @Test
    void shouldFailWhenMetricIsNanOrInfinite() {
        EvaluationGateResult gate = policy.evaluate(
                new EvaluationResult(
                        1,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        1.0,
                        1.0
                )
        );

        assertThat(gate.passed()).isFalse();
        assertThat(gate.violations()).containsExactly(
                "precisionAtK NaN is below required minimum 0.6000",
                "recallAtK Infinity is below required minimum 0.6000"
        );
    }
}
