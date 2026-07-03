package io.qwenbridge.event.snapshot;

public record PipelineContextSnapshot(
        String requestId,
        String query,
        boolean stopped,
        boolean safe,
        String language,
        String intent,
        String decision,
        long timestamp
) {
}
