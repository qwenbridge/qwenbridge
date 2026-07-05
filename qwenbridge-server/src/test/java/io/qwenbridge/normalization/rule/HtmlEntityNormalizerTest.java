package io.qwenbridge.normalization.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlEntityNormalizerTest {

  @Test
  void shouldDecodeCommonHtmlEntities() {
    HtmlEntityNormalizer normalizer = new HtmlEntityNormalizer();

    String result = normalizer.normalize("&lt;script&gt;alert&#x27;x&#x27;&lt;/script&gt;");

    assertThat(result).isEqualTo("<script>alert'x'</script>");
  }

  @Test
  void shouldDecodeAmpersandEntity() {
    HtmlEntityNormalizer normalizer = new HtmlEntityNormalizer();

    String result = normalizer.normalize("A &amp;&amp; B");

    assertThat(result).isEqualTo("A && B");
  }
}
