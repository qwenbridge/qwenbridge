package io.qwenbridge.streaming.session;

import io.qwenbridge.streaming.config.StreamingProperties;
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

    private final ConcurrentMap<String, StreamingSession> sessionsById =
            new ConcurrentHashMap<>();

    public StreamingSessionRegistry(StreamingProperties properties) {
        this.properties = properties;
    }

    public StreamingSession register(String requestId) {
        return register(requestId, properties.sessionTimeoutMs());
    }

    public StreamingSession register(String requestId, long timeoutMs) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        StreamingSession session =
                new StreamingSession(sessionId, requestId, emitter);

        emitter.onCompletion(() -> remove(sessionId));
        emitter.onTimeout(() -> remove(sessionId));
        emitter.onError(error -> remove(sessionId));

        sessionsById.put(sessionId, session);

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

    public int size() {
        return sessionsById.size();
    }

    public boolean remove(String sessionId) {
        StreamingSession removed = sessionsById.remove(sessionId);

        if (removed == null) {
            return false;
        }

        if (removed.close()) {
            removed.emitter().complete();
        }

        return true;
    }

    public void clear() {
        all().forEach(session -> remove(session.sessionId()));
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

    private void send(
            StreamingSession session,
            String eventId,
            String eventName,
            Object payload
    ) {
        if (session.closed()) {
            remove(session.sessionId());
            return;
        }

        try {
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
