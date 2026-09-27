package io.qwenbridge.threat.correlation.rule;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatType;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class ThreatCorrelationRuleLoader {

  @SuppressWarnings("unchecked")
  public List<ThreatCorrelationRule> load(String resourcePath) {
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
                new ThreatCorrelationRule(
                    text(rule, "id"),
                    condition(rule),
                    decimal(rule, "scoreBoost"),
                    ThreatRiskLevel.valueOf(text(rule, "riskLevel")),
                    text(rule, "reason")))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private ThreatCorrelationCondition condition(Map<String, Object> rule) {
    Object whenValue = rule.get("when");

    if (!(whenValue instanceof Map<?, ?> rawWhen)) {
      return new ThreatCorrelationCondition(List.of(), List.of(), List.of());
    }

    Map<String, Object> when = (Map<String, Object>) rawWhen;

    return new ThreatCorrelationCondition(
        types(when, "allOf"), types(when, "anyOf"), types(when, "noneOf"));
  }

  private List<ThreatType> types(Map<String, Object> source, String key) {
    Object value = source.get(key);

    if (!(value instanceof List<?> rawTypes)) {
      return List.of();
    }

    return rawTypes.stream().map(Object::toString).map(ThreatType::valueOf).toList();
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
