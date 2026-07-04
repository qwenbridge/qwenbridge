package io.qwenbridge.normalization.rule;

public interface InputNormalizationRule {
    String name();

    String normalize(String input);
}
