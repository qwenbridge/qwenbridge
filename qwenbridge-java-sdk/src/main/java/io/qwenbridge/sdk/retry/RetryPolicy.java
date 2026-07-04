package io.qwenbridge.sdk.retry;

import java.time.Duration;
import java.util.Objects;

public record RetryPolicy(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff
) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");

        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }

        if (maxBackoff.isNegative() || maxBackoff.isZero()) {
            throw new IllegalArgumentException("maxBackoff must be positive");
        }

        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff must be greater than or equal to initialBackoff"
            );
        }
    }

    public static RetryPolicy disabled() {
        return new RetryPolicy(
                1,
                Duration.ofMillis(100),
                Duration.ofMillis(100)
        );
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(2)
        );
    }
}
