package io.qwenbridge.language;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageServiceTest {

    private final LanguageService languageService = new LanguageService();

    @ParameterizedTest
    @CsvSource({
            "'laptop', en",
            "'gaming laptop', en",
            "'best gaming laptop under 1500 euro', en",
            "'best gamng labtop under 1500 euro', en",
            "'بهترین لپ تاپ گیمینگ زیر ۱۵۰۰ یورو', fa",
            "'لپ تاپ', fa",
            "'أفضل لابتوب ألعاب أقل من 1500 يورو', ar",
            "'1500ユーロ以下の最高のゲーミングノートPC', ja",
            "'bästa gaming laptop under 15000 kronor', sv",
            "'bester gaming laptop unter 1500 euro', de",
            "'meilleur ordinateur portable gaming moins de 1500 euros', fr",
            "'mejor portátil gaming por menos de 1500 euros', es",
            "'1500 euro altı en iyi oyun laptopu', tr",
            "'beste gaming laptop onder 1500 euro', nl"
    })
    void shouldDetectExpectedLanguage(String query, String expectedLanguage) {
        assertThat(languageService.detect(query)).isEqualTo(expectedLanguage);
    }
}