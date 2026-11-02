package io.qwenbridge.sdk.streaming.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Tolerates fields added by newer servers (forward compatibility). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIFailedStreamingPayload(
    String requestId, String code, String message, boolean terminal) implements StreamingPayload {}
