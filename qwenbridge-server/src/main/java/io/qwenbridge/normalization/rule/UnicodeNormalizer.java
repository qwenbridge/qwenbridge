package io.qwenbridge.normalization.rule;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;

@ApplicationScoped
public class UnicodeNormalizer implements InputNormalizationRule {

  @Override
  public int order() {
    return 30;
  }

  @Override
  public String name() {
    return "unicode";
  }

  @Override
  public String normalize(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }

    return Normalizer.normalize(input, Normalizer.Form.NFKC);
  }
}
