package io.qwenbridge.streaming.session;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamingSession {

  private final String sessionId;
  private final String requestId;
  private final Instant createdAt;
  private final AtomicReference<Instant> lastSeen;
  private final SessionSink sink;
  private final AtomicBoolean closed;

  public StreamingSession(String sessionId, String requestId, SessionSink sink) {
    this(
        sessionId,
        requestId,
        Instant.now(),
        new AtomicReference<>(Instant.now()),
        sink,
        new AtomicBoolean(false));
  }

  StreamingSession(
      String sessionId,
      String requestId,
      Instant createdAt,
      AtomicReference<Instant> lastSeen,
      SessionSink sink,
      AtomicBoolean closed) {
    this.sessionId = sessionId;
    this.requestId = requestId;
    this.createdAt = createdAt;
    this.lastSeen = lastSeen;
    this.sink = sink;
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

  public SessionSink sink() {
    return sink;
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
