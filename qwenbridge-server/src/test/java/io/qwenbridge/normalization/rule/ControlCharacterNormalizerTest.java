package io.qwenbridge.normalization.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ControlCharacterNormalizerTest {

  @Test
  void shouldRemoveControlCharactersExceptWhitespaceControls() {
    ControlCharacterNormalizer normalizer = new ControlCharacterNormalizer();

    String result = normalizer.normalize("abc\u0000def\tghi\njkl");

    assertThat(result).isEqualTo("abcdef\tghi\njkl");
  }
}
