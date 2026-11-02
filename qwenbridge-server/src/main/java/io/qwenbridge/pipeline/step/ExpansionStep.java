package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.expansion.ExpansionResult;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.multilingual.ExpansionService;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Multilingual query expansion in the v1 pipeline (QB-286). It runs after the deterministic threat
 * check and the model safety step (order 28, after translation), so a blocked query is never sent
 * to a translation provider. The expanded text is the search-normalized query, which includes typo
 * corrections when QB-291 applied them; the original is never replaced. v1 has no tenant, so the
 * application settings apply. Disabled (the default) it does nothing but report {@code DISABLED}
 * internally; the response and QueryIR then carry no expansion.
 */
@ApplicationScoped
public class ExpansionStep implements PipelineStep<ExpansionResult> {

  private final ExpansionService expansion;

  @Inject
  public ExpansionStep(ExpansionService expansion) {
    this.expansion = expansion;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.NORMALIZATION;
  }

  @Override
  public String name() {
    return "ExpansionStep";
  }

  @Override
  public int order() {
    return 28;
  }

  @Override
  public boolean publishEvents() {
    return false;
  }

  @Override
  public Class<ExpansionResult> resultType() {
    return ExpansionResult.class;
  }

  @Override
  public ExpansionResult execute(ExecutionContext context) {
    String original = context.request().originalQuery();
    NormalizedInput normalized = context.get(NormalizedInput.class);
    String source = normalized == null ? original : normalized.normalizedQuery();
    LanguageResult language = context.get(LanguageResult.class);
    return expansion.expand(
        null,
        original,
        source,
        language == null ? null : language.analysis(),
        EntityDictionary.EMPTY);
  }
}
