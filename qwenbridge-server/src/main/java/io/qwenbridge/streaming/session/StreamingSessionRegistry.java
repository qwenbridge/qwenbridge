package io.qwenbridge.streaming.session;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.streaming.config.StreamingProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class StreamingSessionRegistry {

  private final StreamingProperties properties;
  private final OperationsMetrics metrics;

  private final ConcurrentMap<String, StreamingSession> sessionsById = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, Boolean> cancelledRequests = new ConcurrentHashMap<>();

  public StreamingSessionRegistry(StreamingProperties properties, OperationsMetrics metrics) {
    this.properties = properties;
    this.metrics = metrics;
  }

  public StreamingSession register(String requestId) {
    return register(requestId, new SessionSink.NoOp());
  }

  public StreamingSession register(String requestId, SseEventSink eventSink, Sse sse) {
    return register(requestId, new JaxRsSessionSink(eventSink, sse));
  }

  public StreamingSession register(String requestId, SessionSink sink) {
    String sessionId = UUID.randomUUID().toString();

    cancelledRequests.remove(requestId);

    StreamingSession session = new StreamingSession(sessionId, requestId, sink);

    sessionsById.put(sessionId, session);
    metrics.sessionOpened();

    return session;
  }

  public long sessionTimeoutMs() {
    return properties.sessionTimeoutMs();
  }

  public Optional<StreamingSession> find(String sessionId) {
    return Optional.ofNullable(sessionsById.get(sessionId));
  }

  public List<StreamingSession> all() {
    return List.copyOf(sessionsById.values());
  }

  public List<StreamingSession> findByRequestId(String requestId) {
    return sessionsById.values().stream()
        .filter(session -> session.requestId().equals(requestId))
        .toList();
  }

  public boolean isRequestCancelled(String requestId) {
    return requestId != null && Boolean.TRUE.equals(cancelledRequests.get(requestId));
  }

  public int size() {
    return sessionsById.size();
  }

  public boolean remove(String sessionId) {
    StreamingSession removed = sessionsById.remove(sessionId);

    if (removed == null) {
      return false;
    }

    markCancelledIfNoSessionsRemain(removed.requestId());

    if (removed.close()) {
      metrics.sessionClosed("registry");
      removed.sink().close();
    }

    return true;
  }

  public void clear() {
    all().forEach(session -> remove(session.sessionId()));
    cancelledRequests.clear();
  }

  public void sendToRequest(String requestId, String eventId, String eventName, Object payload) {
    findByRequestId(requestId).forEach(session -> send(session, eventId, eventName, payload));
  }

  public void completeRequest(String requestId) {
    findByRequestId(requestId).forEach(session -> remove(session.sessionId()));

    cancelledRequests.remove(requestId);
  }

  public void failRequest(String requestId, String eventId, String eventName, Object payload) {
    findByRequestId(requestId)
        .forEach(
            session -> {
              send(session, eventId, eventName, payload);
              remove(session.sessionId());
            });

    cancelledRequests.remove(requestId);
  }

  private void markCancelledIfNoSessionsRemain(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return;
    }

    if (findByRequestId(requestId).isEmpty()) {
      cancelledRequests.put(requestId, true);
    }
  }

  private void send(StreamingSession session, String eventId, String eventName, Object payload) {
    if (session.closed() || session.sink().isClosed()) {
      remove(session.sessionId());
      return;
    }

    try {
      metrics.recordSseEvent(eventName);
      session.sink().send(eventId, eventName, payload);

      session.touch();
    } catch (RuntimeException ex) {
      remove(session.sessionId());
    }
  }
}
