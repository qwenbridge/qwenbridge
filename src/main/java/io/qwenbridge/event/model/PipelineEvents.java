package io.qwenbridge.event.model;

import io.qwenbridge.event.snapshot.PipelineContextSnapshot;

public final class PipelineEvents {

    private PipelineEvents() {
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineStarted(
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return started(PipelineStage.PIPELINE, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineCompleted(
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return completed(PipelineStage.PIPELINE, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineFailed(
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return failed(PipelineStage.PIPELINE, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> pipelineStopped(
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return stopped(PipelineStage.PIPELINE, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepStarted(
            PipelineStage stage,
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return started(stage, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepCompleted(
            PipelineStage stage,
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return completed(stage, snapshot, metadata);
    }

    public static PipelineEvent<PipelineContextSnapshot> stepFailed(
            PipelineStage stage,
            PipelineContextSnapshot snapshot,
            PipelineEventMetadata metadata
    ) {
        return failed(stage, snapshot, metadata);
    }

    public static <T> PipelineEvent<T> started(
            PipelineStage stage,
            T payload
    ) {
        return started(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> progress(
            PipelineStage stage,
            T payload
    ) {
        return progress(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> completed(
            PipelineStage stage,
            T payload
    ) {
        return completed(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> failed(
            PipelineStage stage,
            T payload
    ) {
        return failed(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> info(
            PipelineStage stage,
            T payload
    ) {
        return info(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> warning(
            PipelineStage stage,
            T payload
    ) {
        return warning(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> started(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.STARTED, payload, metadata);
    }

    public static <T> PipelineEvent<T> progress(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.PROGRESS, payload, metadata);
    }

    public static <T> PipelineEvent<T> completed(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.COMPLETED, payload, metadata);
    }

    public static <T> PipelineEvent<T> failed(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.FAILED, payload, metadata);
    }

    public static <T> PipelineEvent<T> info(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.INFO, payload, metadata);
    }

    public static <T> PipelineEvent<T> warning(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.WARNING, payload, metadata);
    }

    public static <T> PipelineEvent<T> stopped(
            PipelineStage stage,
            T payload
    ) {
        return stopped(stage, payload, PipelineEventMetadata.empty());
    }

    public static <T> PipelineEvent<T> stopped(
            PipelineStage stage,
            T payload,
            PipelineEventMetadata metadata
    ) {
        return build(stage, PipelineEventType.STOPPED, payload, metadata);
    }

    @SuppressWarnings("unchecked")
    private static <T> PipelineEvent<T> build(
            PipelineStage stage,
            PipelineEventType type,
            T payload,
            PipelineEventMetadata metadata
    ) {
        Class<T> payloadType = payload == null
                ? (Class<T>) Object.class
                : (Class<T>) payload.getClass();

        return new PipelineEvent<>(
                null,
                null,
                stage,
                type,
                metadata,
                payloadType,
                payload
        );
    }
}
