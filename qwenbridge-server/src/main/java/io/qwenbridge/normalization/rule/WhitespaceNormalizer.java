package io.qwenbridge.normalization.rule;

import org.springframework.stereotype.Component;

@Component
public class WhitespaceNormalizer implements InputNormalizationRule {

    @Override
    public String name() {
        return "whitespace";
    }

    @Override
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        return input.trim().replaceAll("\\s+", " ");
    }
}
