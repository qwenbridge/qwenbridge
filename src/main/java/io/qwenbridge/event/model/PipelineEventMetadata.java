package io.qwenbridge.event.model;

public record PipelineEventMetadata(

        String requestId,

        String sessionId,

        String correlationId,

        long sequenceNumber,

        String producer

) {

    public static PipelineEventMetadata empty() {
        return new PipelineEventMetadata(
                "",
                "",
                "",
                0,
                "qwenbridge"
        );
    }

}
