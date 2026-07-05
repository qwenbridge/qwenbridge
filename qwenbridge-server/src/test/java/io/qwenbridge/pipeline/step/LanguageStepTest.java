package io.qwenbridge.pipeline.step;

import io.qwenbridge.language.LanguageService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageStepTest {

    private final LanguageStep step = new LanguageStep(new LanguageService());

    @ParameterizedTest
    @MethodSource("languageQueries")
    void shouldDetectLanguageForRealisticQueries(String query, String expectedLanguage) {
        ExecutionContext context = new ExecutionContext(query);

        LanguageResult result = step.execute(context);

        assertThat(result.language()).isEqualTo(expectedLanguage);
        assertThat(context.stopped()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("ambiguousQueries")
    void shouldReturnUnknownForAmbiguousInput(String query) {
        ExecutionContext context = new ExecutionContext(query);

        LanguageResult result = step.execute(context);

        assertThat(result.language()).isEqualTo("unknown");
        assertThat(context.stopped()).isFalse();
    }

    @Test
    void shouldStoreDetectedLanguageInExecutionContext() {
        ExecutionContext context = new ExecutionContext(
                "What are the best wireless headphones for working from home?"
        );

        LanguageResult result = step.execute(context);
        context.store(LanguageResult.class, result);

        assertThat(context.get(LanguageResult.class).language()).isEqualTo("en");
        assertThat(context.stopped()).isFalse();
    }

    private static Stream<Arguments> languageQueries() {
        return Stream.of(
                Arguments.of("What are the best wireless headphones for working from home?", "en"),
                Arguments.of("بهترین هدفون بی‌سیم برای کار کردن در خانه چیست؟", "fa"),
                Arguments.of("ما هي أفضل سماعات لاسلكية للعمل من المنزل؟", "ar"),
                Arguments.of("自宅で仕事をするための最高のワイヤレスヘッドホンは何ですか？", "ja"),
                Arguments.of("哪些无线耳机最适合在家办公？", "zh"),
                Arguments.of("Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?", "sv"),
                Arguments.of("Welche kabellosen Kopfhörer eignen sich am besten für die Arbeit zu Hause?", "de"),
                Arguments.of("Quels écouteurs sans fil sont les meilleurs pour travailler à domicile ?", "fr"),
                Arguments.of("¿Qué auriculares inalámbricos son mejores para trabajar desde casa?", "es"),
                Arguments.of("Evden çalışmak için en iyi kablosuz kulaklıklar hangileridir?", "tr"),
                Arguments.of("Welke draadloze hoofdtelefoon is het beste om thuis te werken?", "nl")
        );
    }

    private static Stream<Arguments> ambiguousQueries() {
        return Stream.of(
                Arguments.of("12345 !!!"),
                Arguments.of("   "),
                Arguments.of("---"),
                Arguments.of("€€€ $$$")
        );
    }
}