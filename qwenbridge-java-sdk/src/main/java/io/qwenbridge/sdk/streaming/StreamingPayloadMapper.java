package io.qwenbridge.sdk.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.sdk.streaming.payload.AICompletedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AIFailedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AITokenStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.ConnectedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.StreamingPayload;
import io.qwenbridge.sdk.streaming.payload.UnknownStreamingPayload;

import java.util.Objects;

final class StreamingPayloadMapper {

    private final ObjectMapper objectMapper;

    StreamingPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    TypedStreamingEvent map(StreamingEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String eventName = event.eventName();
        String rawData = event.data();

        try {
            return new TypedStreamingEvent(
                    eventName,
                    deserialize(eventName, rawData),
                    rawData
            );
        } catch (JsonProcessingException ex) {
            return new TypedStreamingEvent(
                    eventName,
                    new UnknownStreamingPayload(rawData),
                    rawData
            );
        }
    }

    private StreamingPayload deserialize(String eventName, String rawData)
            throws JsonProcessingException {

        return switch (eventName) {
            case "stream.connected" ->
                    objectMapper.readValue(rawData, ConnectedStreamingPayload.class);
            case "ai.token" ->
                    objectMapper.readValue(rawData, AITokenStreamingPayload.class);
            case "ai.completed" ->
                    objectMapper.readValue(rawData, AICompletedStreamingPayload.class);
            case "ai.failed" ->
                    objectMapper.readValue(rawData, AIFailedStreamingPayload.class);
            default -> new UnknownStreamingPayload(rawData);
        };
    }
}
