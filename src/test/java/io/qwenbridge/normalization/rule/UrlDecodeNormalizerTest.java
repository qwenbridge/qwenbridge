package io.qwenbridge.normalization.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlDecodeNormalizerTest {

    @Test
    void shouldDecodeUrlEncodedInput() {
        UrlDecodeNormalizer normalizer = new UrlDecodeNormalizer();

        String result = normalizer.normalize("%3Cscript%3Ealert%281%29%3C%2Fscript%3E");

        assertThat(result).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void shouldDecodeNestedUrlEncodedInputUpToMaxDepth() {
        UrlDecodeNormalizer normalizer = new UrlDecodeNormalizer();

        String result = normalizer.normalize("%2527%2520OR%25201%253D1");

        assertThat(result).isEqualTo("' OR 1=1");
    }

    @Test
    void shouldKeepInvalidEncodingUnchanged() {
        UrlDecodeNormalizer normalizer = new UrlDecodeNormalizer();

        String result = normalizer.normalize("%E0%A4%A");

        assertThat(result).isEqualTo("%E0%A4%A");
    }
}
