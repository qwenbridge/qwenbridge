package io.qwenbridge.threat.rule;

import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class ThreatRuleLoader {

  @SuppressWarnings("unchecked")
  public List<ThreatPatternRule> load(String resourcePath, ThreatType type) {
    Map<String, Object> root;
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        return List.of();
      }
      root = new Yaml().load(in);
    } catch (java.io.IOException ex) {
      return List.of();
    }

    if (root == null || !root.containsKey("rules")) {
      return List.of();
    }

    List<Map<String, Object>> rules = (List<Map<String, Object>>) root.get("rules");

    return rules.stream()
        .map(
            rule ->
                new ThreatPatternRule(
                    text(rule, "id"),
                    type,
                    Pattern.compile(text(rule, "regex")),
                    ThreatSeverity.valueOf(text(rule, "severity")),
                    decimal(rule, "score"),
                    decimal(rule, "confidence"),
                    text(rule, "evidence"),
                    text(rule, "reason")))
        .toList();
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
    return Double.parseDouble(value.toString());
  }
}
