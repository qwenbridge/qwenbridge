package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.core.intent.IntentAnalyzer;
import io.qwenbridge.core.intent.IntentUnderstanding;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.SchemaKey;
import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.RequestHints;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Intent step (QB-022–QB-027). Two outputs:
 *
 * <ul>
 *   <li>{@link IntentResult}: the legacy v1 intent from the AI analysis, unchanged for
 *       compatibility (and {@code UNKNOWN} when no analysis exists — never a default intent);
 *   <li>{@link IntentUnderstanding}: the deterministic, domain-neutral analysis (typed intents with
 *       spans, retrieval needs, decomposition and the clarification assessment), stored in the
 *       context. It works without any AI provider.
 * </ul>
 */
@ApplicationScoped
public class IntentStep implements PipelineStep<IntentResult> {

  private final IntentAnalyzer analyzer = new IntentAnalyzer();
  private final Supplier<Set<String>> enabledDomains;

  /** Without a registry no domain is known to be enabled. */
  public IntentStep() {
    this(() -> Set.of());
  }

  @Inject
  public IntentStep(SchemaRegistry registry) {
    this(() -> registry.keys().stream().map(SchemaKey::id).collect(Collectors.toUnmodifiableSet()));
  }

  IntentStep(Supplier<Set<String>> enabledDomains) {
    this.enabledDomains = enabledDomains;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.INTENT;
  }

  @Override
  public String name() {
    return "IntentStep";
  }

  @Override
  public int order() {
    return 30;
  }

  @Override
  public Class<IntentResult> resultType() {
    return IntentResult.class;
  }

  @Override
  public IntentResult execute(ExecutionContext context) {
    RequestHints hints =
        Optional.ofNullable(context.get(RequestHints.class)).orElse(RequestHints.none());
    IntentUnderstanding understanding =
        analyzer.analyze(
            context.request().originalQuery(),
            language(context),
            enabledDomains.get(),
            hints.domainHints());
    context.store(IntentUnderstanding.class, understanding);

    SearchAnalysis analysis = context.get(SearchAnalysis.class);
    if (analysis == null) {
      return IntentResult.unknown();
    }
    return analysis.toIntentResult();
  }

  private static Optional<LanguageTag> language(ExecutionContext context) {
    LanguageResult language = context.get(LanguageResult.class);
    if (language != null && language.analysis() != null) {
      return language.analysis().normalizationLanguage();
    }
    if (language != null && !LanguageAnalysis.UNKNOWN.equals(language.language())) {
      return LanguageTag.parse(language.language());
    }
    return Optional.empty();
  }
}
