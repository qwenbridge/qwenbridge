package io.qwenbridge.sdk.streaming.payload;

public sealed interface StreamingPayload permits
        ConnectedStreamingPayload,
        AITokenStreamingPayload,
        AICompletedStreamingPayload,
        AIFailedStreamingPayload,
        UnknownStreamingPayload {
}
