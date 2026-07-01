package io.qwenbridge.streaming.session;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public record StreamingSession(
        String sessionId,
        String requestId,
        Instant createdAt,
        Instant lastSeen,
        SseEmitter emitter,
        AtomicBoolean closed
) {

    public StreamingSession(String sessionId, String requestId, SseEmitter emitter) {
        this(sessionId, requestId, Instant.now(), Instant.now(), emitter, new AtomicBoolean(false));
    }

    public StreamingSession touch() {
        return new StreamingSession(sessionId, requestId, createdAt, Instant.now(), emitter, closed);
    }

    public boolean close() {
        return closed.compareAndSet(false, true);
    }
}
