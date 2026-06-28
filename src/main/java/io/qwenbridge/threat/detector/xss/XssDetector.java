package io.qwenbridge.threat.detector.xss;

import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class XssDetector implements ThreatDetector {

    private static final Pattern SCRIPT_TAG =
            Pattern.compile("(?i)<\\s*/?\\s*script\\b[^>]*>");

    private static final Pattern EVENT_HANDLER =
            Pattern.compile("(?i)\\bon\\w+\\s*=");

    private static final Pattern JAVASCRIPT_URI =
            Pattern.compile("(?i)\\bjavascript\\s*:");

    private static final Pattern DANGEROUS_HTML_TAG =
            Pattern.compile("(?i)<\\s*(iframe|object|embed|svg|math|link|meta)\\b[^>]*>");

    @Override
    public String name() {
        return "xss-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.XSS;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<ThreatFinding> detect(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        if (SCRIPT_TAG.matcher(input).find()) {
            return finding("Script tag detected.", "script tag", 0.92, ThreatSeverity.CRITICAL);
        }

        if (JAVASCRIPT_URI.matcher(input).find()) {
            return finding("JavaScript URI detected.", "javascript:", 0.88, ThreatSeverity.HIGH);
        }

        if (EVENT_HANDLER.matcher(input).find()) {
            return finding("HTML event handler detected.", "on* handler", 0.80, ThreatSeverity.HIGH);
        }

        if (DANGEROUS_HTML_TAG.matcher(input).find()) {
            return finding("Dangerous HTML tag detected.", "dangerous html tag", 0.78, ThreatSeverity.HIGH);
        }

        return List.of();
    }

    private List<ThreatFinding> finding(
            String reason,
            String evidence,
            double score,
            ThreatSeverity severity
    ) {
        return List.of(new ThreatFinding(
                ThreatType.XSS,
                severity,
                score,
                0.90,
                name(),
                evidence,
                reason
        ));
    }
}
