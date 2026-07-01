package io.qwenbridge.streaming.event;

import io.qwenbridge.event.snapshot.PipelineContextSnapshot;

public record SnapshotStreamingPayload(
        PipelineContextSnapshot snapshot
) implements StreamingPayload {
}
