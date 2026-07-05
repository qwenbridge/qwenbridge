package io.qwenbridge.threat.detector.unicode;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

class UnicodeObfuscationDetectorTest {

  private final UnicodeObfuscationDetector detector =
      new UnicodeObfuscationDetector(new ThreatRuleLoader());

  @Test
  void shouldDetectZeroWidthCharacter() {
    var findings = detector.detect("SEL\u200BECT");

    assertThat(findings).isNotEmpty();
    assertThat(findings.getFirst().type()).isEqualTo(ThreatType.UNICODE_OBFUSCATION);
  }

  @Test
  void shouldDetectBidiOverride() {
    var findings = detector.detect("safe\u202Eevil");

    assertThat(findings).isNotEmpty();
    assertThat(findings.getFirst().type()).isEqualTo(ThreatType.UNICODE_OBFUSCATION);
  }

  @Test
  void shouldAllowSafeSearchQuery() {
    var findings = detector.detect("normal product search");

    assertThat(findings).isEmpty();
  }
}
