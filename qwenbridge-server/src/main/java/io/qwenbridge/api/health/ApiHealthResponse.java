package io.qwenbridge.api.health;

import lombok.Builder;

@Builder
public record ApiHealthResponse(String status, String service, String apiVersion) {}
