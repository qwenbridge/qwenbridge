package io.qwenbridge.threat;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ThreatService {

    public ThreatResult analyze(String query) {
        String normalized = query.toLowerCase();

        List<String> reasons = new ArrayList<>();

        if (normalized.matches(".*('.*--|or\\s+1=1|union\\s+select|drop\\s+table).*")) {
            reasons.add("SQL_INJECTION");
        }

        if (normalized.matches(".*(<script|javascript:|onerror=|onload=).*")) {
            reasons.add("XSS");
        }

        if (normalized.matches(".*(ignore previous instructions|system prompt|developer message).*")) {
            reasons.add("PROMPT_INJECTION");
        }

        if (normalized.matches(".*(\\.\\./|/etc/passwd|cmd.exe|powershell).*")) {
            reasons.add("COMMAND_OR_PATH_ABUSE");
        }

        return reasons.isEmpty()
                ? ThreatResult.noThreat()
                : ThreatResult.detected(reasons);
    }
}
