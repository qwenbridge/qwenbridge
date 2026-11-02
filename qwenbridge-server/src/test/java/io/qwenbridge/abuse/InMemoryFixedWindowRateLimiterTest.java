package io.qwenbridge.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryFixedWindowRateLimiterTest {

  // The limiter's windows are aligned to the wall clock. With the system clock, three calls can
  // straddle a window boundary and the third is then legitimately allowed (seen once in batch20
  // master); a fixed clock inside one window makes the test deterministic.
  private static final Instant INSIDE_A_WINDOW = Instant.parse("2026-11-01T12:00:30Z");

  private static AbuseProtectionProperties properties() {
    return new AbuseProtectionProperties(
        true, 1024, 2, 10, 10, 100, 5, Duration.ofMinutes(1), Duration.ofSeconds(1), false);
  }

  @Test
  void rejectsWhenFixedWindowLimitIsExceeded() {
    InMemoryFixedWindowRateLimiter limiter =
        new InMemoryFixedWindowRateLimiter(
            properties(), Clock.fixed(INSIDE_A_WINDOW, ZoneOffset.UTC));

    assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isTrue();
    assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isTrue();

    RateLimitDecision rejected = limiter.consume("ip", "127.0.0.1", 2, 1);

    assertThat(rejected.allowed()).isFalse();
    assertThat(rejected.policy()).isEqualTo("ip");
    assertThat(rejected.remaining()).isZero();
  }

  @Test
  void aNewWindowStartsAFreshCount() {
    var now = new java.util.concurrent.atomic.AtomicReference<>(INSIDE_A_WINDOW);
    Clock clock =
        new Clock() {
          public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          public Instant instant() {
            return now.get();
          }
        };
    InMemoryFixedWindowRateLimiter limiter =
        new InMemoryFixedWindowRateLimiter(properties(), clock);
    limiter.consume("ip", "127.0.0.1", 2, 1);
    limiter.consume("ip", "127.0.0.1", 2, 1);
    assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isFalse();
    now.set(INSIDE_A_WINDOW.plusSeconds(60));
    assertThat(limiter.consume("ip", "127.0.0.1", 2, 1).allowed()).isTrue();
  }
}
