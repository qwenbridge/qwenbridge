package io.qwenbridge.pipeline.step;

import io.qwenbridge.language.LanguageService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import org.springframework.stereotype.Component;

@Component
public class LanguageStep implements PipelineStep<LanguageResult> {

    private final LanguageService languageService;

    public LanguageStep(LanguageService languageService) {
        this.languageService = languageService;
    }

    public String name() { return "LanguageStep"; }
    public int order() { return 10; }
    public Class<LanguageResult> resultType() { return LanguageResult.class; }

    public LanguageResult execute(ExecutionContext context) {
        return new LanguageResult(
                languageService.detect(context.request().originalQuery())
        );
    }
}
