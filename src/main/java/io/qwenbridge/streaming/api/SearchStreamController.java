package io.qwenbridge.streaming.api;

import io.qwenbridge.streaming.session.StreamingSession;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchStreamController {

    private final StreamingSessionRegistry registry;

    @GetMapping(
            value = "/stream/{requestId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@PathVariable String requestId) {
        StreamingSession session = registry.register(requestId);
        return session.emitter();
    }
}
