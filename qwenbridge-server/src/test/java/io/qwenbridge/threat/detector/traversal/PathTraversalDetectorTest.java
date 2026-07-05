package io.qwenbridge.threat.detector.traversal;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

class PathTraversalDetectorTest {

  private final PathTraversalDetector detector = new PathTraversalDetector(new ThreatRuleLoader());

  @Test
  void shouldDetectParentDirectoryTraversal() {
    var findings = detector.detect("../../etc/passwd");

    assertThat(findings).isNotEmpty();
    assertThat(findings.getFirst().type()).isEqualTo(ThreatType.PATH_TRAVERSAL);
  }

  @Test
  void shouldDetectSensitiveUnixPath() {
    var findings = detector.detect("/etc/passwd");

    assertThat(findings).isNotEmpty();
    assertThat(findings.getFirst().type()).isEqualTo(ThreatType.PATH_TRAVERSAL);
  }

  @Test
  void shouldDetectFileUri() {
    var findings = detector.detect("file:///etc/passwd");

    assertThat(findings).isNotEmpty();
    assertThat(findings.getFirst().type()).isEqualTo(ThreatType.PATH_TRAVERSAL);
  }

  @Test
  void shouldAllowSafeSearchQuery() {
    var findings = detector.detect("office desk with file cabinet");

    assertThat(findings).isEmpty();
  }
}
