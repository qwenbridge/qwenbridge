package io.qwenbridge.streaming.listener;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.streaming.event.PipelineEventTerminalPolicy;
import io.qwenbridge.streaming.event.PipelineStreamingEvent;
import io.qwenbridge.streaming.event.PipelineStreamingEventMapper;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SsePipelineEventListener {

    private final StreamingSessionRegistry registry;

    private final PipelineStreamingEventMapper mapper;

    private final PipelineEventTerminalPolicy terminalPolicy;

    @EventListener
    public void onPipelineEvent(PipelineEvent<?> event) {
        PipelineStreamingEvent streamingEvent = mapper.map(event);

        if (streamingEvent.requestId() == null || streamingEvent.requestId().isBlank()) {
            return;
        }

        registry.sendToRequest(
                streamingEvent.requestId(),
                streamingEvent.id(),
                streamingEvent.event(),
                streamingEvent
        );

        if (terminalPolicy.isTerminal(event.stage(), event.type())) {
            registry.completeRequest(streamingEvent.requestId());
        }
    }
}