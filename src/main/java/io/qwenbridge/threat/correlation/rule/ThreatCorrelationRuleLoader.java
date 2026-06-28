package io.qwenbridge.threat.correlation.rule;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatType;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ThreatCorrelationRuleLoader {

    @SuppressWarnings("unchecked")
    public List<ThreatCorrelationRule> load(String resourcePath) {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(resourcePath));

        Map<String, Object> root = yaml.getObject();
        if (root == null || !root.containsKey("rules")) {
            return List.of();
        }

        List<Map<String, Object>> rules = (List<Map<String, Object>>) root.get("rules");

        return rules.stream()
                .map(rule -> new ThreatCorrelationRule(
                        text(rule, "id"),
                        condition(rule),
                        decimal(rule, "scoreBoost"),
                        ThreatRiskLevel.valueOf(text(rule, "riskLevel")),
                        text(rule, "reason")
                ))
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
                types(when, "allOf"),
                types(when, "anyOf"),
                types(when, "noneOf")
        );
    }

    private List<ThreatType> types(Map<String, Object> source, String key) {
        Object value = source.get(key);

        if (!(value instanceof List<?> rawTypes)) {
            return List.of();
        }

        return rawTypes.stream()
                .map(Object::toString)
                .map(ThreatType::valueOf)
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
