package io.qwenbridge.streaming.ai;

import io.qwenbridge.streaming.event.AICompletedStreamingPayload;
import io.qwenbridge.streaming.event.AIFailedStreamingPayload;
import io.qwenbridge.streaming.event.AITokenStreamingPayload;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AIStreamingEventPublisher {

    private final StreamingSessionRegistry registry;

    public void token(String requestId, long tokenIndex, String content) {
        if (invalid(requestId) || content == null || content.isBlank()) {
            return;
        }

        registry.sendToRequest(
                requestId,
                UUID.randomUUID().toString(),
                "ai.token",
                new AITokenStreamingPayload(
                        requestId,
                        tokenIndex,
                        content,
                        false
                )
        );
    }

    public void completed(String requestId, long tokenCount) {
        if (invalid(requestId)) {
            return;
        }

        registry.sendToRequest(
                requestId,
                UUID.randomUUID().toString(),
                "ai.completed",
                new AICompletedStreamingPayload(
                        requestId,
                        tokenCount,
                        false
                )
        );
    }

    public void failed(String requestId, String code, String message) {
        if (invalid(requestId)) {
            return;
        }

        registry.sendToRequest(
                requestId,
                UUID.randomUUID().toString(),
                "ai.failed",
                new AIFailedStreamingPayload(
                        requestId,
                        code,
                        message,
                        false
                )
        );
    }

    private boolean invalid(String requestId) {
        return requestId == null || requestId.isBlank();
    }
}
