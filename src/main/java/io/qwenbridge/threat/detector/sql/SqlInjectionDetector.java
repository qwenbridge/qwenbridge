package io.qwenbridge.threat.detector.sql;

import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SqlInjectionDetector implements ThreatDetector {

    private static final Pattern UNION_SELECT =
            Pattern.compile("(?i)\\bunion\\s+select\\b");

    private static final Pattern BOOLEAN_BYPASS =
            Pattern.compile("(?i)(\\bor\\b|\\band\\b)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?");

    private static final Pattern COMMENT_SEQUENCE =
            Pattern.compile("(?i)(--|#|/\\*)");

    private static final Pattern DESTRUCTIVE_SQL =
            Pattern.compile("(?i)\\b(drop|delete|truncate|alter)\\s+(table|database|schema)\\b");

    @Override
    public String name() {
        return "sql-injection-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.SQL_INJECTION;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<ThreatFinding> detect(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        if (UNION_SELECT.matcher(input).find()) {
            return finding("UNION SELECT pattern detected.", "union select", 0.92, ThreatSeverity.CRITICAL);
        }

        if (BOOLEAN_BYPASS.matcher(input).find()) {
            return finding("SQL boolean bypass pattern detected.", "or/and equality condition", 0.85, ThreatSeverity.HIGH);
        }

        if (DESTRUCTIVE_SQL.matcher(input).find()) {
            return finding("Destructive SQL statement detected.", "destructive SQL keyword", 0.90, ThreatSeverity.CRITICAL);
        }

        if (COMMENT_SEQUENCE.matcher(input).find()) {
            return finding("SQL comment sequence detected.", "SQL comment marker", 0.55, ThreatSeverity.MEDIUM);
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
                ThreatType.SQL_INJECTION,
                severity,
                score,
                0.90,
                name(),
                evidence,
                reason
        ));
    }
}
