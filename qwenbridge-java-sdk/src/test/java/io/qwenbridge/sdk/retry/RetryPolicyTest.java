package io.qwenbridge.sdk.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void shouldCreateDisabledPolicy() {
        RetryPolicy policy = RetryPolicy.disabled();

        assertEquals(1, policy.maxAttempts());
        assertEquals(Duration.ofMillis(100), policy.initialBackoff());
        assertEquals(Duration.ofMillis(100), policy.maxBackoff());
    }

    @Test
    void shouldCreateDefaultPolicy() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertEquals(3, policy.maxAttempts());
        assertEquals(Duration.ofMillis(100), policy.initialBackoff());
        assertEquals(Duration.ofSeconds(2), policy.maxBackoff());
    }

    @Test
    void shouldRejectMaxAttemptsBelowOne() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        0,
                        Duration.ofMillis(100),
                        Duration.ofSeconds(1)
                )
        );

        assertEquals("maxAttempts must be at least 1", exception.getMessage());
    }

    @Test
    void shouldRejectNonPositiveInitialBackoff() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ZERO,
                        Duration.ofSeconds(1)
                )
        );

        assertEquals("initialBackoff must be positive", exception.getMessage());
    }

    @Test
    void shouldRejectNonPositiveMaxBackoff() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofMillis(100),
                        Duration.ZERO
                )
        );

        assertEquals("maxBackoff must be positive", exception.getMessage());
    }

    @Test
    void shouldRejectMaxBackoffLowerThanInitialBackoff() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofMillis(100)
                )
        );

        assertEquals(
                "maxBackoff must be greater than or equal to initialBackoff",
                exception.getMessage()
        );
    }
}
