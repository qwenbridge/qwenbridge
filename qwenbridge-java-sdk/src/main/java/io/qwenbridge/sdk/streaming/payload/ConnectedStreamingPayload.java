package io.qwenbridge.sdk.streaming.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Tolerates fields added by newer servers (forward compatibility). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectedStreamingPayload(String requestId, String sessionId)
    implements StreamingPayload {}
