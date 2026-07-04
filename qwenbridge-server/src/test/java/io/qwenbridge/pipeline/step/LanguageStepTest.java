package io.qwenbridge.pipeline.step;

import io.qwenbridge.language.LanguageService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageStepTest {

    private final LanguageStep step = new LanguageStep(new LanguageService());

    @Test
    void shouldDetectPersianLanguage() {
        ExecutionContext context = new ExecutionContext("میز");
        LanguageResult result = step.execute(context);
        assertThat(result.language()).isEqualTo("fa");
    }

    @Test
    void shouldDetectEnglishLanguage() {
        ExecutionContext context = new ExecutionContext("desk");
        LanguageResult result = step.execute(context);
        assertThat(result.language()).isEqualTo("en");
    }

    @Test
    void shouldDetectJapaneseKanaLanguage() {
        ExecutionContext context = new ExecutionContext("テーブル");
        LanguageResult result = step.execute(context);
        assertThat(result.language()).isEqualTo("ja");
    }

    @Test
    void shouldDetectChineseLanguage() {
        ExecutionContext context = new ExecutionContext("桌子");
        LanguageResult result = step.execute(context);
        assertThat(result.language()).isEqualTo("zh");
    }

    @Test
    void shouldReturnUnknownForAmbiguousSymbols() {
        ExecutionContext context = new ExecutionContext("12345 !!!");
        LanguageResult result = step.execute(context);
        assertThat(result.language()).isEqualTo("unknown");
    }

    @Test
    void languageDetectionMustNotBlockUnknownLanguage() {
        ExecutionContext context = new ExecutionContext("12345 !!!");
        LanguageResult result = step.execute(context);

        assertThat(result.language()).isEqualTo("unknown");
        assertThat(context.stopped()).isFalse();
    }
}
