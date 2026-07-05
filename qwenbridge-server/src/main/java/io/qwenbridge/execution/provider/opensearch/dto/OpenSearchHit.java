package io.qwenbridge.execution.provider.opensearch.dto;

import java.util.Map;

public record OpenSearchHit(String id, double score, Map<String, Object> source) {}
