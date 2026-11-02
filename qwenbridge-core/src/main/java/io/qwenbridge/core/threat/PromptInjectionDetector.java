package io.qwenbridge.core.threat;

import io.qwenbridge.core.confidence.Confidence;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based, multilingual prompt-injection detection (QB-065, QB-067). It covers instruction
 * override and system-prompt disclosure in English, Swedish and Persian, role takeover, and chat
 * template or role markers that try to smuggle a new conversation turn into data. The markers are
 * language-neutral and are the main signal for retrieved content.
 *
 * <p>Detection is only a signal. The trust boundary does not depend on it: untrusted text is passed
 * to models as quoted data and model output is constrained, whether or not anything matched.
 */
public final class PromptInjectionDetector implements ThreatDetector {

  public static final String ID = "core-prompt-injection";

  private static final int FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;
  private static final String S = "[\\s\\u200c]+";
  private static final String P = "[\\s\\u200c]*";

  private record Rule(String id, ThreatSeverity severity, String evidence, Pattern pattern) {
    boolean contentOnly() {
      return "role-line".equals(id);
    }
  }

  private static final List<Rule> RULES =
      List.of(
          new Rule(
              "override-en",
              ThreatSeverity.HIGH,
              "instruction override",
              Pattern.compile(
                  "\\b(?:ignore|disregard|forget|override)\\s+(?:all\\s+|any\\s+|the\\s+|your\\s+)?"
                      + "(?:previous|prior|above|earlier|preceding|system|developer)\\s+"
                      + "(?:instructions?|prompts?|rules|messages?)\\b",
                  FLAGS)),
          new Rule(
              "disclosure-en",
              ThreatSeverity.HIGH,
              "system prompt disclosure",
              Pattern.compile(
                  "\\b(?:reveal|show|print|display|dump|repeat|leak)\\s+(?:me\\s+)?(?:the\\s+|your\\s+)?"
                      + "(?:system|developer|hidden|initial)\\s+(?:prompt|message|instructions?)\\b",
                  FLAGS)),
          new Rule(
              "override-sv",
              ThreatSeverity.HIGH,
              "instruction override (sv)",
              Pattern.compile(
                  "(?<!\\p{L})(?:ignorera|strunta\\s+i|glöm|bortse\\s+från)\\s+(?:alla\\s+)?"
                      + "(?:tidigare|föregående|ovanstående)\\s+"
                      + "(?:instruktioner(?:na)?|regler(?:na)?|direktiv(?:en)?)(?!\\p{L})",
                  FLAGS)),
          new Rule(
              "disclosure-sv",
              ThreatSeverity.HIGH,
              "system prompt disclosure (sv)",
              Pattern.compile(
                  "(?<!\\p{L})(?:visa|avslöja|skriv\\s+ut|upprepa)\\s+(?:din\\s+|den\\s+|dina\\s+)?"
                      + "(?:system|dolda)[\\s-]?(?:prompt(?:en)?|instruktioner(?:na)?)(?!\\p{L})",
                  FLAGS)),
          new Rule(
              "override-fa",
              ThreatSeverity.HIGH,
              "instruction override (fa)",
              Pattern.compile(
                  "(?:(?:دستورالعمل|دستورات|دستورهای|قوانین|قواعد)\\S*"
                      + S
                      + "(?:قبلی|پیشین|بالا)\\S*"
                      + S
                      + "(?:را"
                      + S
                      + ")?(?:نادیده|فراموش))"
                      + "|(?:(?:نادیده"
                      + P
                      + "بگیر|فراموش"
                      + P
                      + "کن)\\S*"
                      + S
                      + "(?:همه"
                      + S
                      + ")?(?:دستورالعمل|دستورات|قوانین)\\S*"
                      + S
                      + "(?:قبلی|پیشین))",
                  FLAGS)),
          new Rule(
              "disclosure-fa",
              ThreatSeverity.HIGH,
              "system prompt disclosure (fa)",
              Pattern.compile(
                  "(?:پرامپت|دستورالعمل)\\S*"
                      + S
                      + "سیستم\\S*"
                      + S
                      + "(?:را"
                      + S
                      + ")?(?:نشان|فاش|بنویس|تکرار)",
                  FLAGS)),
          new Rule(
              "chat-template-marker",
              ThreatSeverity.HIGH,
              "chat template or role marker",
              Pattern.compile(
                  "<\\|(?:im_start|im_end|system|assistant|user|endoftext)\\|>|\\[/?INST\\]|<</?SYS>>",
                  FLAGS)),
          new Rule(
              "role-line",
              ThreatSeverity.MEDIUM,
              "conversation role line",
              Pattern.compile("^\\s*(?:system|assistant|developer)\\s*:", FLAGS)),
          new Rule(
              "role-takeover",
              ThreatSeverity.MEDIUM,
              "role takeover",
              Pattern.compile(
                  "\\b(?:you\\s+are\\s+now|from\\s+now\\s+on\\s+you\\s+are)\\b", FLAGS)));

  @Override
  public String id() {
    return ID;
  }

  @Override
  public ThreatSignal.Source source() {
    return ThreatSignal.Source.RULE;
  }

  @Override
  public List<ThreatSignal> detect(ThreatInput input) {
    List<ThreatSignal> signals = new ArrayList<>();
    for (Rule rule : RULES) {
      if (rule.contentOnly() && input.zone() == TrustZone.USER_INPUT) {
        continue; // A role line in the user's own query is not a smuggled conversation turn.
      }
      if (rule.pattern().matcher(input.text()).find()) {
        signals.add(
            new ThreatSignal(
                ThreatCategory.PROMPT_INJECTION,
                rule.severity(),
                Confidence.heuristic(),
                ThreatSignal.Source.RULE,
                ID,
                rule.id(),
                rule.evidence()));
      }
    }
    return signals;
  }
}
