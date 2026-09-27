package io.qwenbridge.normalization.rule;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
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
