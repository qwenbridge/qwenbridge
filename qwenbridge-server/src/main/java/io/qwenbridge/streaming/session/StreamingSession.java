package io.qwenbridge.streaming.session;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class StreamingSession {

  private final String sessionId;
  private final String requestId;
  private final Instant createdAt;
  private final AtomicReference<Instant> lastSeen;
  private final SseEmitter emitter;
  private final AtomicBoolean closed;

  public StreamingSession(String sessionId, String requestId, SseEmitter emitter) {
    this(
        sessionId,
        requestId,
        Instant.now(),
        new AtomicReference<>(Instant.now()),
        emitter,
        new AtomicBoolean(false));
  }

  StreamingSession(
      String sessionId,
      String requestId,
      Instant createdAt,
      AtomicReference<Instant> lastSeen,
      SseEmitter emitter,
      AtomicBoolean closed) {
    this.sessionId = sessionId;
    this.requestId = requestId;
    this.createdAt = createdAt;
    this.lastSeen = lastSeen;
    this.emitter = emitter;
    this.closed = closed;
  }

  public String sessionId() {
    return sessionId;
  }

  public String requestId() {
    return requestId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant lastSeen() {
    return lastSeen.get();
  }

  public SseEmitter emitter() {
    return emitter;
  }

  public boolean closed() {
    return closed.get();
  }

  public void touch() {
    lastSeen.set(Instant.now());
  }

  public boolean close() {
    return closed.compareAndSet(false, true);
  }
}
