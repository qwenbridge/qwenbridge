package io.qwenbridge.sdk.streaming;

public record StreamingEvent(
        String eventName,
        String data
) {
}
