package io.qwenbridge.streaming.session;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class StreamingSessionRegistry {

    private static final long DEFAULT_TIMEOUT_MS = 0L;

    private final ConcurrentMap<String, StreamingSession> sessions =
            new ConcurrentHashMap<>();

    public StreamingSession register() {
        return register(DEFAULT_TIMEOUT_MS);
    }

    public StreamingSession register(long timeoutMs) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        StreamingSession session =
                new StreamingSession(sessionId, emitter);

        emitter.onCompletion(() -> remove(sessionId));
        emitter.onTimeout(() -> remove(sessionId));
        emitter.onError(error -> remove(sessionId));

        sessions.put(sessionId, session);

        return session;
    }

    public Optional<StreamingSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public List<StreamingSession> all() {
        Collection<StreamingSession> values = sessions.values();
        return List.copyOf(values);
    }

    public int size() {
        return sessions.size();
    }

    public boolean remove(String sessionId) {
        StreamingSession removed = sessions.remove(sessionId);

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

    public void broadcast(String eventName, Object payload) {
        all().forEach(session -> send(session, eventName, payload));
    }

    private void send(
            StreamingSession session,
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
                            .name(eventName)
                            .id(UUID.randomUUID().toString())
                            .data(payload)
            );
            session.touch();
        } catch (IOException | IllegalStateException ex) {
            remove(session.sessionId());
        }
    }
}
