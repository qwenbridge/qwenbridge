package io.qwenbridge.threat.detector.ldap;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LdapInjectionDetectorTest {

    private final LdapInjectionDetector detector =
            new LdapInjectionDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectObjectClassWildcardProbe() {
        var findings = detector.detect("(objectClass=*)");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.LDAP_INJECTION);
    }

    @Test
    void shouldDetectOrWildcardBypass() {
        var findings = detector.detect("(|(uid=*)(userPassword=*))");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.LDAP_INJECTION);
    }

    @Test
    void shouldDetectWildcardFilter() {
        var findings = detector.detect("(cn=*)");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.LDAP_INJECTION);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("ldap tutorial book");

        assertThat(findings).isEmpty();
    }
}
