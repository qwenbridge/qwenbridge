package io.qwenbridge.streaming.session;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class StreamingSession {

    private final String sessionId;
    private final SseEmitter emitter;
    private final Instant createdAt;
    private final AtomicReference<Instant> lastActivity;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StreamingSession(
            String sessionId,
            SseEmitter emitter
    ) {
        this.sessionId = sessionId;
        this.emitter = emitter;
        this.createdAt = Instant.now();
        this.lastActivity = new AtomicReference<>(createdAt);
    }

    public String sessionId() {
        return sessionId;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastActivity() {
        return lastActivity.get();
    }

    public boolean closed() {
        return closed.get();
    }

    public void touch() {
        lastActivity.set(Instant.now());
    }

    public boolean close() {
        return closed.compareAndSet(false, true);
    }
}
