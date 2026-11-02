package io.qwenbridge.sdk.streaming.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Tolerates fields added by newer servers (forward compatibility). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AICompletedStreamingPayload(String requestId, long tokenCount, boolean terminal)
    implements StreamingPayload {}
