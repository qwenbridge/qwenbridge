package io.qwenbridge.normalization.rule;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class UnicodeNormalizer implements InputNormalizationRule {

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
