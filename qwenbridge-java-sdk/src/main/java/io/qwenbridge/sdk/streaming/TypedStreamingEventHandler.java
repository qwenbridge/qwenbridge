package io.qwenbridge.sdk.streaming;

@FunctionalInterface
public interface TypedStreamingEventHandler {

    void onEvent(TypedStreamingEvent event);
}
