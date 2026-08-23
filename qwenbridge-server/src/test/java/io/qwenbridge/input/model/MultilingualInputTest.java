package io.qwenbridge.input.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MultilingualInputTest {

  @Test
  void shouldPreserveEnglishInputExactly() {
    String input = "best wireless headphones";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreservePersianInputExactly() {
    String input = "  یک لپ‌تاپ سبک برای برنامه‌نویسی می‌خواهم  ";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreserveArabicInputExactly() {
    String input = "أريد هاتفًا بكاميرا جيدة";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreserveSwedishInputExactly() {
    String input = "Jag vill ha en lätt bärbar dator";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreserveChineseInputExactly() {
    String input = "我想要一台轻便的笔记本电脑";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreserveJapaneseInputExactly() {
    String input = "軽いノートパソコンが欲しい";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldPreserveEmojiAndUnicodeExactly() {
    String input = "لپ‌تاپ خوب under 1500€";

    MultilingualInput multilingualInput = MultilingualInput.of(input);

    assertThat(multilingualInput.originalText()).isEqualTo(input);
  }

  @Test
  void shouldKeepDeclaredLanguageLocaleAndSourceSeparate() {
    MultilingualInput multilingualInput =
        MultilingualInput.of("میز", "fa", Locale.forLanguageTag("sv-SE"), InputSource.SDK);

    assertThat(multilingualInput.declaredLanguageOptional()).contains("fa");
    assertThat(multilingualInput.localeOptional()).contains(Locale.forLanguageTag("sv-SE"));
    assertThat(multilingualInput.source()).isEqualTo(InputSource.SDK);
  }

  @Test
  void shouldAllowMissingOptionalMetadata() {
    MultilingualInput multilingualInput = MultilingualInput.of("table");

    assertThat(multilingualInput.declaredLanguageOptional()).isEmpty();
    assertThat(multilingualInput.localeOptional()).isEmpty();
    assertThat(multilingualInput.source()).isEqualTo(InputSource.UNKNOWN);
  }

  @Test
  void shouldRejectNullOriginalText() {
    assertThatNullPointerException()
        .isThrownBy(() -> MultilingualInput.of(null))
        .withMessage("originalText must not be null");
  }

  @Test
  void shouldCanonicalizeDeclaredLanguageToLowercase() {
    MultilingualInput input =
        MultilingualInput.of("table", "SV", Locale.forLanguageTag("sv-SE"), InputSource.API);

    assertThat(input.declaredLanguageOptional()).contains("sv");
  }
}
