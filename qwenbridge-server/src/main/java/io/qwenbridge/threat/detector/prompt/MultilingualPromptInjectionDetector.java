package io.qwenbridge.threat.detector.prompt;

import io.qwenbridge.core.threat.PromptInjectionDetector;
import io.qwenbridge.core.threat.ThreatInput;
import io.qwenbridge.core.threat.TrustZone;
import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * Adds the Core multilingual prompt-injection rules (QB-065, QB-067) to the user-input baseline:
 * Swedish and Persian instruction override and prompt disclosure, and chat-template or role
 * markers. English overrides are already covered by {@code prompt-injection.yml}, so those Core
 * rules are skipped here to avoid reporting the same match twice.
 */
@Singleton
public class MultilingualPromptInjectionDetector implements ThreatDetector {

  private static final Set<String> COVERED_BY_YAML =
      Set.of("override-en", "disclosure-en", "role-takeover");

  private final PromptInjectionDetector rules = new PromptInjectionDetector();

  @Override
  public String name() {
    return "multilingual-prompt-injection-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.PROMPT_INJECTION;
  }

  @Override
  public int order() {
    return 31;
  }

  @Override
  public List<ThreatFinding> detect(String input) {
    if (input == null || input.isBlank()) {
      return List.of();
    }
    return rules.detect(ThreatInput.of(input, TrustZone.USER_INPUT)).stream()
        .filter(signal -> !COVERED_BY_YAML.contains(signal.ruleId()))
        .map(
            signal -> {
              boolean high =
                  signal.severity().compareTo(io.qwenbridge.core.threat.ThreatSeverity.HIGH) >= 0;
              // Scores mirror the YAML rules of the same severity; decisions use severity only.
              return new ThreatFinding(
                  ThreatType.PROMPT_INJECTION,
                  high ? ThreatSeverity.HIGH : ThreatSeverity.MEDIUM,
                  high ? 0.78 : 0.52,
                  high ? 0.88 : 0.78,
                  name(),
                  signal.evidence(),
                  "Prompt injection pattern detected (" + signal.evidence() + ").");
            })
        .toList();
  }
}
