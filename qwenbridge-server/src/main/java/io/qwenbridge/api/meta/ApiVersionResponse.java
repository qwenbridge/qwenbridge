package io.qwenbridge.api.meta;

import lombok.Builder;

@Builder
public record ApiVersionResponse(
    String name, String version, String apiVersion, String javaVersion) {}
