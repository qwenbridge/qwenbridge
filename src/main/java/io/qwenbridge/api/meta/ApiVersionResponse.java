package io.qwenbridge.api.meta;

public record ApiVersionResponse(
        String name,
        String version,
        String apiVersion,
        String javaVersion
) {
}
