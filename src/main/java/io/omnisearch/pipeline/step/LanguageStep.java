package io.omnisearch.pipeline.step;

import io.omnisearch.language.LanguageService;
import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.LanguageResult;
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
