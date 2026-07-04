package io.qwenbridge.threat.explanation;

import org.springframework.stereotype.Component;

@Component
public class PlainTextThreatExplanationRenderer implements ThreatExplanationRenderer {

    @Override
    public String render(ThreatExplanation explanation) {
        if (explanation == null) {
            explanation = ThreatExplanation.none();
        }

        StringBuilder builder = new StringBuilder();

        builder.append("Threat Explanation").append(System.lineSeparator());

        for (ThreatExplanationItem item : explanation.items()) {
            builder.append("• ")
                    .append(item.message())
                    .append(" [")
                    .append(item.type())
                    .append(" / ")
                    .append(item.severity())
                    .append(" / ")
                    .append(item.source())
                    .append("]")
                    .append(System.lineSeparator());
        }

        for (String rule : explanation.matchedCorrelationRules()) {
            builder.append("• Correlation rule \"")
                    .append(rule)
                    .append("\" matched")
                    .append(System.lineSeparator());
        }

        builder.append(System.lineSeparator());
        builder.append("Final Risk:").append(System.lineSeparator());
        builder.append(explanation.riskLevel()).append(System.lineSeparator());

        builder.append(System.lineSeparator());
        builder.append("Decision:").append(System.lineSeparator());
        builder.append(explanation.decision());

        return builder.toString();
    }
}
