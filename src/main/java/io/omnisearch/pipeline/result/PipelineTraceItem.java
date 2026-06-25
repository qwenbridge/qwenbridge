package io.omnisearch.pipeline.result;

public record PipelineTraceItem(
        String step,
        String status,
        long durationMs
) {}
