package io.qwenbridge.streaming.session;

import io.qwenbridge.streaming.config.StreamingProperties;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class StreamingSessionRegistry {

    private final StreamingProperties properties;
    private final OperationsMetrics metrics;

    private final ConcurrentMap<String, StreamingSession> sessionsById =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Boolean> cancelledRequests =
            new ConcurrentHashMap<>();

    public StreamingSessionRegistry(StreamingProperties properties, OperationsMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    public StreamingSession register(String requestId) {
        return register(requestId, properties.sessionTimeoutMs());
    }

    public StreamingSession register(String requestId, long timeoutMs) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        cancelledRequests.remove(requestId);

        StreamingSession session =
                new StreamingSession(sessionId, requestId, emitter);

        emitter.onCompletion(() -> removeAfterEmitterCompletion(sessionId));
        emitter.onTimeout(() -> removeAfterEmitterCompletion(sessionId));
        emitter.onError(error -> removeAfterEmitterCompletion(sessionId));

        sessionsById.put(sessionId, session);        metrics.sessionOpened();

        return session;
    }

    public Optional<StreamingSession> find(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public List<StreamingSession> all() {
        return List.copyOf(sessionsById.values());
    }

    public List<StreamingSession> findByRequestId(String requestId) {
        return sessionsById.values()
                .stream()
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

        if (removed.close()) {            metrics.sessionClosed("registry");
            removed.emitter().complete();
        }

        return true;
    }

    public void clear() {
        all().forEach(session -> remove(session.sessionId()));
        cancelledRequests.clear();
    }

    public void sendToRequest(
            String requestId,
            String eventId,
            String eventName,
            Object payload
    ) {
        findByRequestId(requestId)
                .forEach(session -> send(
                        session,
                        eventId,
                        eventName,
                        payload
                ));
    }

    public void completeRequest(String requestId) {
        findByRequestId(requestId)
                .forEach(session -> remove(session.sessionId()));

        cancelledRequests.remove(requestId);
    }

    public void failRequest(
            String requestId,
            String eventId,
            String eventName,
            Object payload
    ) {
        findByRequestId(requestId)
                .forEach(session -> {
                    send(session, eventId, eventName, payload);
                    remove(session.sessionId());
                });

        cancelledRequests.remove(requestId);
    }

    private void removeAfterEmitterCompletion(String sessionId) {
        StreamingSession removed = sessionsById.remove(sessionId);

        if (removed != null) {
            removed.close();            metrics.sessionClosed("emitter");
            markCancelledIfNoSessionsRemain(removed.requestId());
        }
    }

    private void markCancelledIfNoSessionsRemain(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        if (findByRequestId(requestId).isEmpty()) {
            cancelledRequests.put(requestId, true);
        }
    }

    private void send(
            StreamingSession session,
            String eventId,
            String eventName,
            Object payload
    ) {
        if (session.closed()) {
            removeAfterEmitterCompletion(session.sessionId());
            return;
        }

        try {            metrics.recordSseEvent(eventName);
            session.emitter().send(
                    SseEmitter.event()
                            .id(eventId)
                            .name(eventName)
                            .data(payload)
            );

            session.touch();
        } catch (IOException | IllegalStateException ex) {
            remove(session.sessionId());
        }
    }
}
