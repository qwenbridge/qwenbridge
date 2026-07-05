package io.qwenbridge.normalization.rule;

import org.springframework.stereotype.Component;

@Component
public class ControlCharacterNormalizer implements InputNormalizationRule {

  @Override
  public String name() {
    return "control-character";
  }

  @Override
  public String normalize(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }

    return input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
  }
}
