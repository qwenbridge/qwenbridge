package io.qwenbridge.threat.rule;

import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads pattern rules from YAML. Rules are a mandatory control (QB-065, QB-069), so loading fails
 * closed: a missing, unreadable or empty rule file, or a rule without an id, a valid pattern or a
 * known severity, is a startup error rather than a detector that silently finds nothing (audit S4).
 */
@ApplicationScoped
public class ThreatRuleLoader {

  @SuppressWarnings("unchecked")
  public List<ThreatPatternRule> load(String resourcePath, ThreatType type) {
    Object root;
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Threat rule file missing: " + resourcePath);
      }
      root = new Yaml().load(in);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Threat rule file unreadable: " + resourcePath, ex);
    }
    if (!(root instanceof Map<?, ?> map) || !(map.get("rules") instanceof List<?> rules)) {
      throw new IllegalStateException("Threat rule file has no rules list: " + resourcePath);
    }
    if (rules.isEmpty()) {
      throw new IllegalStateException("Threat rule file has no rules: " + resourcePath);
    }
    return rules.stream()
        .map(rule -> toRule((Map<String, Object>) rule, type, resourcePath))
        .toList();
  }

  private ThreatPatternRule toRule(Map<String, Object> rule, ThreatType type, String file) {
    String id = text(rule, "id");
    String regex = text(rule, "regex");
    if (id.isBlank() || regex.isBlank()) {
      throw new IllegalStateException("Threat rule without id or regex in " + file);
    }
    try {
      return new ThreatPatternRule(
          id,
          type,
          Pattern.compile(regex),
          ThreatSeverity.valueOf(text(rule, "severity")),
          decimal(rule, "score"),
          decimal(rule, "confidence"),
          text(rule, "evidence"),
          text(rule, "reason"));
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("Invalid threat rule " + id + " in " + file, ex);
    }
  }

  private String text(Map<String, Object> rule, String key) {
    Object value = rule.get(key);
    return value == null ? "" : value.toString();
  }

  private double decimal(Map<String, Object> rule, String key) {
    Object value = rule.get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      throw new IllegalArgumentException("missing " + key);
    }
    return Double.parseDouble(value.toString());
  }
}
