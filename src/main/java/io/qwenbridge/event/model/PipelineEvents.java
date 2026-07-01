package io.qwenbridge.event.model;

import io.qwenbridge.event.snapshot.PipelineContextSnapshot;

public final class PipelineEvents {

    private PipelineEvents() {
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineStarted(
            PipelineContextSnapshot snapshot
    ) {
        return started(PipelineStage.PIPELINE, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineCompleted(
            PipelineContextSnapshot snapshot
    ) {
        return completed(PipelineStage.PIPELINE, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineFailed(
            PipelineContextSnapshot snapshot
    ) {
        return failed(PipelineStage.PIPELINE, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineStopped(
            PipelineContextSnapshot snapshot
    ) {
        return warning(PipelineStage.PIPELINE, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepStarted(
            PipelineStage stage,
            PipelineContextSnapshot snapshot
    ) {
        return started(stage, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepCompleted(
            PipelineStage stage,
            PipelineContextSnapshot snapshot
    ) {
        return completed(stage, snapshot);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepFailed(
            PipelineStage stage,
            PipelineContextSnapshot snapshot
    ) {
        return failed(stage, snapshot);
    }

    public static <T> PipelineEvent<T> started(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.STARTED, payload);
    }

    public static <T> PipelineEvent<T> progress(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.PROGRESS, payload);
    }

    public static <T> PipelineEvent<T> completed(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.COMPLETED, payload);
    }

    public static <T> PipelineEvent<T> failed(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.FAILED, payload);
    }

    public static <T> PipelineEvent<T> info(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.INFO, payload);
    }

    public static <T> PipelineEvent<T> warning(
            PipelineStage stage,
            T payload
    ) {
        return build(stage, PipelineEventType.WARNING, payload);
    }

    @SuppressWarnings("unchecked")
    private static <T> PipelineEvent<T> build(
            PipelineStage stage,
            PipelineEventType type,
            T payload
    ) {

        Class<T> payloadType = payload == null
                ? (Class<T>) Object.class
                : (Class<T>) payload.getClass();

        return new PipelineEvent<>(
                null,
                null,
                stage,
                type,
                PipelineEventMetadata.empty(),
                payloadType,
                payload
        );
    }
}
