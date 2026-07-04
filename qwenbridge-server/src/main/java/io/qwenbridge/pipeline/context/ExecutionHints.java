package io.qwenbridge.pipeline.context;

import java.time.Duration;
import java.util.Objects;

public record ExecutionHints(

        String provider,
        Duration timeout,
        boolean cacheEnabled,
        boolean debug

) {

    public ExecutionHints {

        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

    }

}