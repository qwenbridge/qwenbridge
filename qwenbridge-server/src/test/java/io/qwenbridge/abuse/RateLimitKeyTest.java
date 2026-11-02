package io.qwenbridge.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Audit S5: API-key buckets must not collide the way {@code String.hashCode()} does. */
class RateLimitKeyTest {

  @Test
  void keysWithEqualHashCodesGetDifferentBuckets() {
    assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
    assertThat(AbuseProtectionFilter.fingerprint("Aa"))
        .isNotEqualTo(AbuseProtectionFilter.fingerprint("BB"));
  }

  @Test
  void fingerprintIsStableAndDoesNotContainTheKey() {
    String key = "qb-live-key-123456";
    assertThat(AbuseProtectionFilter.fingerprint(key))
        .isEqualTo(AbuseProtectionFilter.fingerprint(" " + key + " "))
        .hasSize(32)
        .doesNotContain("123456");
  }
}
