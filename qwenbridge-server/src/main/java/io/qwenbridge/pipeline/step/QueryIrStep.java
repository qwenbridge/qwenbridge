package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.extraction.Extraction;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.intent.IntentUnderstanding;
import io.qwenbridge.core.queryir.IrViolation;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.retrieval.CrossLanguageRetrieval;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.language.LanguageAnalysis;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.BoundSchema;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.QueryIrResult;
import io.qwenbridge.pipeline.result.RequestHints;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jboss.logging.Logger;

/**
 * Assembles the universal {@link QueryIR} for the request (QB-029–QB-036): language, intents,
 * retrieval needs and decomposition from the intent step, and entities, constraints and semantic
 * signals from deterministic extraction. The request time is the explicit reference time; a client
 * {@code timezone} context entry enables calendar expressions. The schema of the first enabled
 * hinted domain binds fields; otherwise field names are proposals.
 *
 * <p>The QueryIR is validated before it is stored. It is informational in v1 (the legacy pipeline
 * still plans with its own analysis); planning on it starts in Section 2.
 */
@ApplicationScoped
public class QueryIrStep implements PipelineStep<QueryIrResult> {

  private static final Logger LOG = Logger.getLogger(QueryIrStep.class);

  private final Function<String, Optional<DomainSchema>> schemas;

  public QueryIrStep() {
    this(id -> Optional.empty());
  }

  @Inject
  public QueryIrStep(SchemaRegistry registry) {
    this(registry::latest);
  }

  QueryIrStep(Function<String, Optional<DomainSchema>> schemas) {
    this.schemas = schemas;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.INTENT;
  }

  @Override
  public String name() {
    return "QueryIrStep";
  }

  @Override
  public int order() {
    return 31;
  }

  /** Assembling the IR is internal; it does not add events to the public SSE stream. */
  @Override
  public boolean publishEvents() {
    return false;
  }

  @Override
  public Class<QueryIrResult> resultType() {
    return QueryIrResult.class;
  }

  @Override
  public QueryIrResult execute(ExecutionContext context) {
    String text = context.request().originalQuery();
    RequestHints hints =
        Optional.ofNullable(context.get(RequestHints.class)).orElse(RequestHints.none());
    Optional<LanguageTag> language = language(context);
    Optional<DomainSchema> bound =
        hints.domainHints().stream().map(schemas).flatMap(Optional::stream).findFirst();
    bound.ifPresent(schema -> context.store(BoundSchema.class, new BoundSchema(schema)));

    ExtractionContext extractionContext =
        new ExtractionContext(
            language,
            context
                .request()
                .input()
                .localeOptional()
                .map(l -> l.toLanguageTag())
                .flatMap(LanguageTag::parse),
            Optional.of(context.request().startedAt()),
            zone(hints),
            bound,
            EntityDictionary.EMPTY);
    Extraction extraction = ConstraintExtractor.extract(text, extractionContext);
    context.store(Extraction.class, extraction);

    QueryIR ir = QueryIR.of(text, detected(context));
    IntentUnderstanding understanding = context.get(IntentUnderstanding.class);
    if (understanding != null) {
      ir = understanding.applyTo(ir);
    }
    ir = extraction.applyTo(ir);
    ir = withContentLanguage(ir, context);
    // QB-291: typo metadata (QueryIR 1.1), once typo correction is enabled; added last so that no
    // later rebuild drops it. Constraints stay grounded in the original text.
    var correction = context.get(io.qwenbridge.core.spelling.CorrectionResult.class);
    if (io.qwenbridge.spelling.TypoService.reportable(correction))
      ir = ir.withCorrection(io.qwenbridge.spelling.TypoService.toQueryIr(correction));
    // QB-286: multilingual expansion metadata (QueryIR 1.1), once expansion is enabled.
    var expansion = context.get(io.qwenbridge.core.expansion.ExpansionResult.class);
    if (expansion != null && expansion.reportable())
      ir = ir.withExpansion(io.qwenbridge.multilingual.ExpansionService.toQueryIr(expansion));

    List<IrViolation> violations = QueryIrValidator.validate(ir);
    if (!violations.isEmpty()) {
      // Should not happen: extraction only produces grounded, typed elements. Never expose an
      // invalid IR; report the defect without user text.
      LOG.warnf(
          "Assembled QueryIR is invalid: %s", violations.stream().map(IrViolation::code).toList());
      return QueryIrResult.invalid(violations);
    }
    return QueryIrResult.of(ir);
  }

  /**
   * QB-020: when the client names the content language and it differs from the query's, the IR asks
   * for cross-language retrieval. An undetermined query language also counts as different.
   */
  private static QueryIR withContentLanguage(QueryIR ir, ExecutionContext context) {
    Optional<LanguageTag> content = TranslationStep.contentLanguage(context);
    if (content.isEmpty()) {
      return ir;
    }
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        ir.entities(),
        ir.constraints(),
        ir.semanticSignals(),
        CrossLanguageRetrieval.withContentLanguages(
            ir.retrievalHints(), ir.language(), List.of(content.get())),
        ir.subqueries(),
        ir.extensions());
  }

  private static Optional<ZoneId> zone(RequestHints hints) {
    String id = hints.context().get("timezone");
    if (id == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(ZoneId.of(id));
    } catch (DateTimeException e) {
      return Optional.empty();
    }
  }

  private static LanguageTag detected(ExecutionContext context) {
    LanguageResult language = context.get(LanguageResult.class);
    if (language != null && language.analysis() != null) {
      return language.analysis().detection().language();
    }
    return LanguageTag.UNDETERMINED;
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
