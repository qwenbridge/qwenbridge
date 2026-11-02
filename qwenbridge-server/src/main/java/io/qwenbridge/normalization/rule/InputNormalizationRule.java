package io.qwenbridge.normalization.rule;

public interface InputNormalizationRule {
  String name();

  default int order() {
    return 100;
  }

  String normalize(String input);
}
