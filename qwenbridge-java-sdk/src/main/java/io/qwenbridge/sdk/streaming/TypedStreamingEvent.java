package io.qwenbridge.sdk.streaming;

import io.qwenbridge.sdk.streaming.payload.StreamingPayload;
import java.util.Objects;

public record TypedStreamingEvent(String eventName, StreamingPayload payload, String rawData) {
  public TypedStreamingEvent {
    Objects.requireNonNull(eventName, "eventName must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(rawData, "rawData must not be null");
  }
}
