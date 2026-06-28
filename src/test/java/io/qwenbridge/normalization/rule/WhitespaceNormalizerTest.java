package io.qwenbridge.normalization.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhitespaceNormalizerTest {

    @Test
    void shouldCollapseWhitespace() {
        WhitespaceNormalizer normalizer = new WhitespaceNormalizer();

        String result = normalizer.normalize("  SELECT     *\nFROM\tusers  ");

        assertThat(result).isEqualTo("SELECT * FROM users");
    }
}
