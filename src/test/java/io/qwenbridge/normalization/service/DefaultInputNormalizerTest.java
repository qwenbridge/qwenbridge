package io.qwenbridge.normalization.service;

import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.rule.ControlCharacterNormalizer;
import io.qwenbridge.normalization.rule.HtmlEntityNormalizer;
import io.qwenbridge.normalization.rule.InputNormalizationRule;
import io.qwenbridge.normalization.rule.UnicodeNormalizer;
import io.qwenbridge.normalization.rule.UrlDecodeNormalizer;
import io.qwenbridge.normalization.rule.WhitespaceNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultInputNormalizerTest {

    @Test
    void shouldNormalizeInputThroughAllRules() {
        List<InputNormalizationRule> rules = List.of(
                new UnicodeNormalizer(),
                new UrlDecodeNormalizer(),
                new HtmlEntityNormalizer(),
                new ControlCharacterNormalizer(),
                new WhitespaceNormalizer()
        );

        DefaultInputNormalizer normalizer = new DefaultInputNormalizer(rules);

        NormalizedInput result = normalizer.normalize("  %253Cscript%253Ealert%25281%2529%253C%252Fscript%253E  ");

        assertThat(result.originalQuery()).isEqualTo("  %253Cscript%253Ealert%25281%2529%253C%252Fscript%253E  ");
        assertThat(result.normalizedQuery()).isEqualTo("<script>alert(1)</script>");
        assertThat(result.changed()).isTrue();
        assertThat(result.trace()).hasSize(5);
        assertThat(result.trace()).anyMatch(item -> item.changed());
    }

    @Test
    void shouldHandleNullInput() {
        DefaultInputNormalizer normalizer = new DefaultInputNormalizer(List.of());

        NormalizedInput result = normalizer.normalize(null);

        assertThat(result.originalQuery()).isEmpty();
        assertThat(result.normalizedQuery()).isEmpty();
        assertThat(result.changed()).isFalse();
    }
}
