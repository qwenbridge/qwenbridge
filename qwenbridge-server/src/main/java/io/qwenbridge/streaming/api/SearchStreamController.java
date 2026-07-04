package io.qwenbridge.streaming.api;

import io.qwenbridge.streaming.api.validation.StreamRequestIdValidator;
import io.qwenbridge.streaming.event.ConnectedStreamingPayload;
import io.qwenbridge.streaming.session.StreamingSession;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchStreamController {

    private final StreamingSessionRegistry registry;
    private final StreamRequestIdValidator requestIdValidator;

    @GetMapping(
            value = "/stream/{requestId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@PathVariable String requestId) {
        requestIdValidator.validate(requestId);

        StreamingSession session = registry.register(requestId);

        try {
            session.emitter().send(
                    SseEmitter.event()
                            .name("stream.connected")
                            .data(new ConnectedStreamingPayload(
                                    requestId,
                                    session.sessionId()
                            ))
            );
        } catch (IOException | IllegalStateException ex) {
            registry.remove(session.sessionId());

            throw new IllegalStateException(
                    "Unable to establish SSE stream for requestId: " + requestId,
                    ex
            );
        }

        return session.emitter();
    }
}