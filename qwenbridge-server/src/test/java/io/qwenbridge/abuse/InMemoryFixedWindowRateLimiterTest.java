package io.qwenbridge.abuse;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFixedWindowRateLimiterTest {

    @Test
    void rejectsWhenFixedWindowLimitIsExceeded() {
        AbuseProtectionProperties properties = new AbuseProtectionProperties(
                true,
                1024,
                2,
                10,
                10,
                100,
                5,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1),
                false
        );
        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(properties);

        assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isTrue();
        assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isTrue();

        RateLimitDecision rejected = limiter.consume("ip", "127.0.0.1", 2, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.policy()).isEqualTo("ip");
        assertThat(rejected.remaining()).isZero();
    }
}
