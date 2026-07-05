package io.qwenbridge.sdk.streaming;

@FunctionalInterface
public interface StreamingEventHandler {

  void onEvent(StreamingEvent event);
}
