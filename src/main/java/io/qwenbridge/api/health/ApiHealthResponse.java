package io.qwenbridge.api.health;

public record ApiHealthResponse(
        String status,
        String service,
        String apiVersion
) {
}
