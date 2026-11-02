package io.qwenbridge.normalization.rule;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class UrlDecodeNormalizer implements InputNormalizationRule {

  private static final int MAX_DECODE_DEPTH = 3;

  @Override
  public int order() {
    return 10;
  }

  @Override
  public String name() {
    return "url-decode";
  }

  @Override
  public String normalize(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }

    String current = input;

    for (int i = 0; i < MAX_DECODE_DEPTH; i++) {
      String decoded = decodeOnce(current);

      if (decoded.equals(current)) {
        return current;
      }

      current = decoded;
    }

    return current;
  }

  private String decodeOnce(String input) {
    try {
      return URLDecoder.decode(input.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ignored) {
      return input;
    }
  }
}
