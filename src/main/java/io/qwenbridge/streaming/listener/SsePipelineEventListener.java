package io.qwenbridge.streaming.listener;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
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

    @EventListener
    public void onPipelineEvent(PipelineEvent<?> event) {
        String requestId = resolveRequestId(event);

        if (requestId == null || requestId.isBlank()) {
            return;
        }

        registry.sendToRequest(
                requestId,
                event.type().name().toLowerCase(),
                mapper.map(event)
        );
    }

    private String resolveRequestId(PipelineEvent<?> event) {
        if (event.metadata() != null && !event.metadata().requestId().isBlank()) {
            return event.metadata().requestId();
        }

        if (event.payload() instanceof PipelineContextSnapshot snapshot) {
            return snapshot.requestId();
        }

        return "";
    }
}
