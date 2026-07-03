package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEventType;
import io.qwenbridge.event.model.PipelineStage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEventTerminalPolicyTest {

    private final PipelineEventTerminalPolicy policy =
            new PipelineEventTerminalPolicy();

    @Test
    void shouldTreatCompletedPipelineEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.PIPELINE,
                PipelineEventType.COMPLETED
        )).isTrue();
    }

    @Test
    void shouldTreatFailedPipelineEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.PIPELINE,
                PipelineEventType.FAILED
        )).isTrue();
    }

    @Test
    void shouldTreatStoppedPipelineEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.PIPELINE,
                PipelineEventType.STOPPED
        )).isTrue();
    }

    @Test
    void shouldNotTreatNonPipelineCompletedEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.SEARCH,
                PipelineEventType.COMPLETED
        )).isFalse();
    }

    @Test
    void shouldNotTreatPipelineStartedEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.PIPELINE,
                PipelineEventType.STARTED
        )).isFalse();
    }

    @Test
    void shouldNotTreatPipelineWarningEventAsTerminal() {
        assertThat(policy.isTerminal(
                PipelineStage.PIPELINE,
                PipelineEventType.WARNING
        )).isFalse();
    }

    @Test
    void shouldTreatOnlyPipelineFailedAsFailure() {
        assertThat(policy.isFailure(
                PipelineStage.PIPELINE,
                PipelineEventType.FAILED
        )).isTrue();

        assertThat(policy.isFailure(
                PipelineStage.SEARCH,
                PipelineEventType.FAILED
        )).isFalse();

        assertThat(policy.isFailure(
                PipelineStage.PIPELINE,
                PipelineEventType.COMPLETED
        )).isFalse();
    }

}