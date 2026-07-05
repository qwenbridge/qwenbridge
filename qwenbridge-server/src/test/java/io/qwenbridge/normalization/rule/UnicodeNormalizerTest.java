package io.qwenbridge.normalization.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnicodeNormalizerTest {

  @Test
  void shouldNormalizeFullWidthCharacters() {
    UnicodeNormalizer normalizer = new UnicodeNormalizer();

    String result = normalizer.normalize("ＳＥＬＥＣＴ ＊ ＦＲＯＭ users");

    assertThat(result).isEqualTo("SELECT * FROM users");
  }

  @Test
  void shouldKeepNullAsNull() {
    UnicodeNormalizer normalizer = new UnicodeNormalizer();

    assertThat(normalizer.normalize(null)).isNull();
  }
}
