package io.qwenbridge.sdk.streaming.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Tolerates fields added by newer servers (forward compatibility). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AITokenStreamingPayload(
    String requestId, long tokenIndex, String content, boolean terminal)
    implements StreamingPayload {}
