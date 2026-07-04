package io.qwenbridge.sdk.retry;

import java.time.Duration;
import java.util.Objects;

public final class ExponentialBackoff {

    private ExponentialBackoff() {
    }

    public static Duration delayForAttempt(RetryPolicy policy, int attempt) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }

        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration delay = policy.initialBackoff().multipliedBy(multiplier);

        if (delay.compareTo(policy.maxBackoff()) > 0) {
            return policy.maxBackoff();
        }

        return delay;
    }
}
