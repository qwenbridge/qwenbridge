package io.qwenbridge.streaming.event;

import io.qwenbridge.event.snapshot.PipelineContextSnapshot;

public record SnapshotStreamingPayload(
    String query,
    boolean stopped,
    boolean safe,
    String language,
    String intent,
    String decision,
    long timestamp)
    implements StreamingPayload {

  public static SnapshotStreamingPayload from(PipelineContextSnapshot snapshot) {
    return new SnapshotStreamingPayload(
        snapshot.query(),
        snapshot.stopped(),
        snapshot.safe(),
        snapshot.language(),
        snapshot.intent(),
        snapshot.decision(),
        snapshot.timestamp());
  }
}
