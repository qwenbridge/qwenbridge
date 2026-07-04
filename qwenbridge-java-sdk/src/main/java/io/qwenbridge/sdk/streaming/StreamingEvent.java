package io.qwenbridge.sdk.streaming;

public record StreamingEvent(
        String event,
        String data
) {
}
