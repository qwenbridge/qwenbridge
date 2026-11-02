package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.confidence.ConfidenceAssessment;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationResult;
import io.qwenbridge.core.translation.TranslationStage;
import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.security.ApiIdentity;
import io.qwenbridge.threat.ThreatService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CommerceSearchService {
  @jakarta.inject.Inject io.qwenbridge.connectors.CommerceBackends backends;
  @jakarta.inject.Inject io.qwenbridge.platform.ManagedPacks packs;
  @jakarta.inject.Inject io.qwenbridge.platform.Entitlements entitlements;

  /** QB-291: typo processing with the caller's tenant settings (absent in unit tests). */
  @jakarta.inject.Inject io.qwenbridge.spelling.TypoService typo;

  @jakarta.inject.Inject io.qwenbridge.translation.TranslationStages translations;

  // QB-286: multilingual expansion (reported once enabled; disabled by default).
  @jakarta.inject.Inject io.qwenbridge.multilingual.ExpansionService expander;
  @jakarta.inject.Inject CommercePlanning planning;

  private record Metadata(
      String parent,
      String cursor,
      Map<String, Long> timings,
      List<PolicyDecision> policies,
      List<String> locked,
      Retrieval retrieval,
      Interpretation interpretation,
      List<SubQueryResult> subqueries,
      List<ClarificationQuestion> clarifications) {
    Metadata(
        String parent,
        String cursor,
        Map<String, Long> timings,
        List<PolicyDecision> policies,
        List<String> locked) {
      this(parent, cursor, timings, policies, locked, null, null, null, null);
    }

    Metadata(
        String parent,
        String cursor,
        Map<String, Long> timings,
        List<PolicyDecision> policies,
        List<String> locked,
        Retrieval retrieval,
        Interpretation interpretation,
        List<SubQueryResult> subqueries) {
      this(parent, cursor, timings, policies, locked, retrieval, interpretation, subqueries, null);
    }
  }

  private final SearchAnalysisService analysis;
  private final ThreatService threats;
  private final AIService ai;
  private final CommerceSchemaRegistry schemas;
  private final OpenSearchProvider search;
  private final CommerceEvidenceStore evidence;
  private final CommerceAnalytics analytics;
  private final CommerceReranker reranker;
  private final io.qwenbridge.language.LanguageService languages;

  public CommerceSearchService(
      SearchAnalysisService analysis,
      ThreatService threats,
      AIService ai,
      CommerceSchemaRegistry schemas,
      OpenSearchProvider search,
      CommerceEvidenceStore evidence,
      CommerceAnalytics analytics,
      CommerceReranker reranker,
      io.qwenbridge.language.LanguageService languages) {
    this.analysis = analysis;
    this.threats = threats;
    this.ai = ai;
    this.schemas = schemas;
    this.search = search;
    this.evidence = evidence;
    this.analytics = analytics;
    this.reranker = reranker;
    this.languages = languages;
  }

  public SearchResponse search(SearchRequest request, ApiIdentity.Principal caller) {
    return execute(request, caller, null, null);
  }

  /**
   * A follow-up turn whose earlier constraints come from a validated conversation context (QB-140,
   * QB-144) instead of a stored response. They are merged by the Core rules and then validated with
   * the new turn's constraints like any other plan.
   */
  public SearchResponse searchInContext(
      SearchRequest request, ApiIdentity.Principal caller, List<Constraint> inherited) {
    return execute(request, caller, null, List.copyOf(inherited));
  }

  public SearchResponse refine(
      String previousId, SearchRequest request, ApiIdentity.Principal caller) {
    var previous = analytics.previous(caller, previousId);
    return refineVerified(previous, request, caller);
  }

  // Only trusted session/proposal services may supply a previously owned server response.
  public SearchResponse refineVerified(
      SearchResponse previous, SearchRequest request, ApiIdentity.Principal caller) {
    if (previous.queryIR() != null && !caller.tenant().equals(previous.queryIR().tenant()))
      throw new jakarta.ws.rs.ForbiddenException();
    if (!previous.status().equals("OK"))
      throw new IllegalArgumentException("Only a completed search can be refined");
    if (!previous.queryIR().schemaId().equals(request.schemaId())
        || previous.queryIR().schemaVersion() != request.schemaVersion())
      throw new IllegalArgumentException("Refinement must retain the schema version");
    return execute(request, caller, previous, null);
  }

  /**
   * QB-291: every search reports its typo processing (once enabled for the caller's tenant), also
   * when it stops early; the correction is computed after authorization, inside {@link #run}.
   */
  private SearchResponse execute(
      SearchRequest request,
      ApiIdentity.Principal caller,
      SearchResponse previous,
      List<Constraint> inherited) {
    io.qwenbridge.core.spelling.CorrectionResult[] correction =
        new io.qwenbridge.core.spelling.CorrectionResult[1];
    io.qwenbridge.core.expansion.ExpansionResult[] expansion =
        new io.qwenbridge.core.expansion.ExpansionResult[1];
    SearchResponse response = run(request, caller, previous, inherited, correction, expansion);
    var view = io.qwenbridge.spelling.SpellingView.of(correction[0]);
    if (view != null) response = response.withSpelling(view);
    var expanded = io.qwenbridge.multilingual.ExpansionView.of(expansion[0]);
    return expanded == null ? response : response.withExpansion(expanded);
  }

  private SearchResponse run(
      SearchRequest request,
      ApiIdentity.Principal caller,
      SearchResponse previous,
      List<Constraint> inherited,
      io.qwenbridge.core.spelling.CorrectionResult[] correction,
      io.qwenbridge.core.expansion.ExpansionResult[] expansion) {
    caller.require("search");
    entitlements.require(caller.tenant(), "search");
    if (typo != null) {
      var spellingLanguage = languages.analyze(request.query(), java.util.Optional.empty());
      String searchForm =
          io.qwenbridge.core.text.SearchTextNormalizer.normalize(
                  request.query(), spellingLanguage.normalizationLanguage())
              .normalized();
      correction[0] = typo.correct(caller.tenant(), request.query(), searchForm, spellingLanguage);
    }
    packs.withPack(caller.tenant(), "commerce", p -> p.manifest());
    var stages = new java.util.LinkedHashMap<String, Long>();
    long started = System.nanoTime();
    String id = request.requestId() == null ? UUID.randomUUID().toString() : request.requestId();
    if (!id.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid request identifier");
    if (!request.retainedConstraints().isEmpty())
      throw new IllegalArgumentException(
          "Use the refinement endpoint to retain server-verified constraints");
    Schema schema = schemas.get(caller.tenant(), request.schemaId(), request.schemaVersion());
    if (!schema.fields().keySet().containsAll(request.lockedFields()))
      throw new IllegalArgumentException("Unknown locked field");
    long phase = System.nanoTime();
    var deterministic = threats.analyzeDetailed(request.query());
    if (!deterministic.safe())
      return stopped(
          request,
          caller,
          id,
          "BLOCKED",
          "Deterministic security policy blocked this request",
          started);

    stages.put("input-validation", (System.nanoTime() - phase) / 1_000_000);
    // QB-182: stop between stages once the request is cancelled or out of time.
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    phase = System.nanoTime();
    SearchAnalysis a = analysis.analyze(request.query());
    stages.put("analysis", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    phase = System.nanoTime();
    if (a == null || !"ALLOW".equals(a.modelThreatDecision()))
      return stopped(
          request,
          caller,
          id,
          a != null && "BLOCK".equals(a.modelThreatDecision()) ? "BLOCKED" : "REVIEW",
          "Model safety decision unavailable or requires review",
          started);

    // QB-286: multilingual expansion for the caller's tenant, only after both safety checks
    // allowed the query; it expands the normalized (and, when applied, typo-corrected) query and
    // never replaces the original. Hard constraints stay grounded in the original below.
    if (expander != null && expander.enabled(caller.tenant())) {
      String expansionSource =
          correction[0] != null && correction[0].mode().applies() && correction[0].changed()
              ? correction[0].corrected()
              : io.qwenbridge.core.text.SearchTextNormalizer.normalize(
                      request.query(),
                      languages
                          .analyze(request.query(), java.util.Optional.empty())
                          .normalizationLanguage())
                  .normalized();
      expansion[0] =
          expander.expand(
              caller.tenant(),
              request.query(),
              expansionSource,
              languages.analyze(expansionSource, java.util.Optional.empty()),
              io.qwenbridge.core.extraction.EntityDictionary.EMPTY);
      stages.put("expansion", (System.nanoTime() - phase) / 1_000_000);
      phase = System.nanoTime();
    }
    String translated = a.rewrites().isEmpty() ? request.query() : a.rewrites().getFirst();
    // QB-291: without a model rewrite, an applied correction becomes the retrieval text.
    // Constraint evidence stays verified against the original query below.
    if (a.rewrites().isEmpty()
        && correction[0] != null
        && correction[0].mode().applies()
        && correction[0].changed()) translated = correction[0].corrected();
    List<String> translationFailures = new ArrayList<>();
    // Translate mixed text and text detected as non-English; undetermined text is not translated
    // on a guess (short Latin text is no longer assumed to be English, QB-008).
    var language = languages.analyze(translated, java.util.Optional.empty());
    if (language.requiresTranslationTo("en"))
      translated =
          translate(
              translated,
              language.mixed()
                  ? new LanguageTag("mul")
                  : LanguageTag.parse(language.primaryLanguage()).orElse(LanguageTag.UNDETERMINED),
              "en",
              translationFailures);
    translated = CommerceExtraction.render(translated, a.constraints());
    if (!"en".equals(request.catalogLanguage()))
      translated =
          translate(
              translated, new LanguageTag("en"), request.catalogLanguage(), translationFailures);
    Translation translation =
        CommerceExtraction.translation(
            request.query(),
            languages.detect(request.query()),
            request.catalogLanguage(),
            translated,
            translationFailures);
    var questions = new ArrayList<>(a.ambiguities());
    questions.addAll(translation.issues());
    if (!a.semanticValidated()
        || a.intent() == io.qwenbridge.intent.IntentType.UNKNOWN
        || a.intentConfidence() < 0.6)
      questions.add("Please clarify what product or comparison you want.");

    stages.put("translation", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    phase = System.nanoTime();
    List<Constraint> constraints = new ArrayList<>(a.constraints());
    for (Constraint c : constraints) c.evidence().verify(request.query());
    for (Constraint c : CommerceExtraction.explicitPrices(request.query()))
      if (constraints.stream()
          .noneMatch(
              x ->
                  x.field().equals(c.field())
                      && x.operator() == c.operator()
                      && x.value().equals(c.value()))) constraints.add(c);
    if (inherited != null && !inherited.isEmpty()) {
      var merged =
          ConversationContexts.merge(
              inherited,
              constraints,
              CommerceExtraction.clearedFields(request.query(), translated));
      constraints = new ArrayList<>(merged);
    }
    if (previous != null) {
      // A deliberate relaxation is expressed in language, without a manual filter UI.
      var cleared = CommerceExtraction.clearedFields(request.query(), translated);
      constraints.removeIf(c -> cleared.contains(c.field()));
      for (Constraint old : previous.queryIR().constraints())
        if (!cleared.contains(old.field()) && !replaced(old, constraints))
          constraints.add(
              new Constraint(
                  old.field(),
                  old.operator(),
                  old.value(),
                  new Evidence(
                      old.evidence().source().equals("original")
                          ? "evidence:" + previous.evidence().getFirst().id()
                          : old.evidence().source(),
                      old.evidence().start(),
                      old.evidence().end(),
                      old.evidence().quote())));
    }
    if (request.planned())
      return executePlanned(
          request,
          caller,
          id,
          stages,
          started,
          translation,
          a,
          constraints,
          questions,
          translated,
          schema,
          previous);
    Plan plan;
    try {
      plan = CommerceCompiler.validate(schema, caller.tenant(), constraints);
    } catch (IllegalArgumentException ex) {
      questions.add(ex.getMessage());
      return response(
          request,
          caller,
          id,
          "CLARIFICATION_REQUIRED",
          translation,
          a,
          null,
          questions,
          SearchResultSet.empty(),
          "not-run",
          started);
    }
    if (!questions.isEmpty())
      return response(
          request,
          caller,
          id,
          "CLARIFICATION_REQUIRED",
          translation,
          a,
          plan,
          questions,
          SearchResultSet.empty(),
          "not-run",
          started);

    stages.put("constraint-validation", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    var decisions =
        io.qwenbridge.security.Authorization.domainPolicies(
            caller, "commerce", "search", Map.of("query", request.query(), "plan", plan));
    var trace =
        decisions.decisions().stream()
            .map(t -> new PolicyDecision(t.pack(), t.version(), t.rule(), t.effect(), t.reason()))
            .toList();
    var locked = new java.util.HashSet<>(request.lockedFields());
    if (previous != null) locked.addAll(previous.lockedFields());
    String parent =
        previous == null || previous.evidence().isEmpty()
            ? null
            : previous.evidence().getFirst().id();
    if (!decisions.allowed())
      return response(
          request,
          caller,
          id,
          "BLOCKED",
          translation,
          a,
          plan,
          trace.stream().map(PolicyDecision::reason).toList(),
          SearchResultSet.empty(),
          "not-run",
          started,
          new Metadata(parent, null, stages, trace, List.copyOf(locked)));
    phase = System.nanoTime();
    List<Double> embedding = List.of();
    // Validate backend/mode before spending an AI embedding call.
    var backend = backends.backend(caller.tenant(), request.backend());
    if (!backend.modes().contains(request.mode()))
      throw new jakarta.ws.rs.ClientErrorException(
          "Backend does not support " + request.mode(), 422);
    if (!request.mode().equals("KEYWORD"))
      embedding = ai.embed(new EmbeddingRequest(translated)).vector();
    stages.put("embedding", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    phase = System.nanoTime();
    var page =
        backends.search(
            caller,
            request.backend(),
            translated,
            plan,
            request.mode(),
            embedding,
            request.limit(),
            request.cursor());
    var results = page.results();
    stages.put("execution", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    phase = System.nanoTime();
    String ranking = "backend-baseline";
    if (request.rerank()) {
      var ranked = reranker.rerank(caller, translated, results);
      results = ranked.results();
      ranking = ranked.method();
    }
    stages.put("ranking", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    return response(
        request,
        caller,
        id,
        "OK",
        translation,
        a,
        plan,
        List.of(),
        results,
        ranking,
        started,
        new Metadata(parent, page.nextCursor(), stages, trace, List.copyOf(locked)));
  }

  /**
   * The {@code commerce-search/1.0} execution (QB-085 – QB-090). Everything up to verified
   * constraints is shared with apiVersion 2 (safety, model analysis, translation, evidence checks,
   * refinement). From there the query is interpreted over the universal QueryIR, validated per
   * scope against the Commerce schema, and executed by the Core planner on the unified SPI with the
   * neutral filter semantics.
   */
  private SearchResponse executePlanned(
      SearchRequest request,
      ApiIdentity.Principal caller,
      String id,
      Map<String, Long> stages,
      long started,
      Translation translation,
      SearchAnalysis a,
      List<Constraint> constraints,
      List<String> questions,
      String translated,
      Schema schema,
      SearchResponse previous) {
    long phase = System.nanoTime();
    var ir =
        CommerceInterpretation.interpret(
            request.query(),
            LanguageTag.parse(translation.sourceLanguage()),
            constraints,
            request.catalogLanguage());
    var scopes = CommerceInterpretation.scopes(ir, constraints);
    var locked = new java.util.HashSet<>(request.lockedFields());
    if (previous != null) locked.addAll(previous.lockedFields());
    String parent =
        previous == null || previous.evidence().isEmpty()
            ? null
            : previous.evidence().getFirst().id();
    List<ClarificationQuestion> clarifications = new ArrayList<>();
    // Questions known before interpretation (model ambiguities, translation issues, an unclear
    // request) carry no field: they are VAGUE.
    for (String q : questions) clarifications.add(CommerceClarifications.vague(q));
    List<Plan> plans = new ArrayList<>();
    List<io.qwenbridge.core.validation.ValidationReport> reports = new ArrayList<>();
    List<ConstraintStatus> statuses = new ArrayList<>();
    for (var scope : scopes) {
      try {
        plans.add(CommerceCompiler.validate(schema, caller.tenant(), scope.commerceConstraints()));
      } catch (IllegalArgumentException ex) {
        questions.add(ex.getMessage());
        clarifications.add(CommerceClarifications.of(ex));
      }
      var report = CommerceInterpretation.validate(scope.ir());
      reports.add(report);
      for (var assessment : report.assessments()) {
        var constraint =
            scope.ir().constraints().stream()
                .filter(c -> c.id().equals(assessment.constraintId()))
                .findFirst();
        statuses.add(
            new ConstraintStatus(
                assessment.constraintId(),
                constraint.map(io.qwenbridge.core.queryir.Constraint::field).orElse(""),
                constraint.map(c -> c.operator().name()).orElse(""),
                assessment.status().name(),
                assessment.note(),
                scope.id().orElse(null)));
        if (assessment.blocking())
          clarifications.add(
              CommerceClarifications.of(
                  assessment, constraint.map(io.qwenbridge.core.queryir.Constraint::field)));
        if (assessment.blocking())
          questions.add(
              "Constraint "
                  + constraint.map(io.qwenbridge.core.queryir.Constraint::field).orElse("")
                  + " cannot be applied: "
                  + assessment.note());
      }
      if (!report.lostNegations().isEmpty()) {
        questions.add("An exclusion could not be preserved; please rephrase it.");
        clarifications.add(
            CommerceClarifications.vague(
                "An exclusion could not be preserved; please rephrase it."));
      }
    }
    var interpretation =
        new Interpretation(
            ir.version().toString(),
            ir.intents().stream().map(io.qwenbridge.core.queryir.Intent::type).toList(),
            ir.retrievalHints().modes().stream().map(Enum::name).sorted().toList(),
            ir.semanticSignals().stream()
                .map(
                    g ->
                        new Signal(
                            g.concept(),
                            g.polarity().name(),
                            g.span().map(io.qwenbridge.core.queryir.Span::text).orElse("")))
                .toList(),
            statuses,
            ir.extensions().get(CommerceQueryExtension.NAMESPACE).data());
    Plan display =
        plans.size() == 1 && scopes.size() == 1
            ? plans.getFirst()
            : new Plan(
                1, schema.id(), schema.version(), caller.tenant(), constraints, schema.fields());
    stages.put("interpretation", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    if (!questions.isEmpty())
      return response(
          request,
          caller,
          id,
          "CLARIFICATION_REQUIRED",
          translation,
          a,
          display,
          questions,
          SearchResultSet.empty(),
          "not-run",
          started,
          new Metadata(
              parent,
              null,
              stages,
              List.of(),
              List.copyOf(locked),
              null,
              interpretation,
              null,
              clarifications));
    List<PolicyDecision> trace = new ArrayList<>();
    boolean allowed = true;
    for (Plan plan : plans) {
      var decisions =
          io.qwenbridge.security.Authorization.domainPolicies(
              caller, "commerce", "search", Map.of("query", request.query(), "plan", plan));
      allowed &= decisions.allowed();
      decisions
          .decisions()
          .forEach(
              t ->
                  trace.add(
                      new PolicyDecision(t.pack(), t.version(), t.rule(), t.effect(), t.reason())));
    }
    if (!allowed)
      return response(
          request,
          caller,
          id,
          "BLOCKED",
          translation,
          a,
          display,
          trace.stream().map(PolicyDecision::reason).toList(),
          SearchResultSet.empty(),
          "not-run",
          started,
          new Metadata(
              parent, null, stages, trace, List.copyOf(locked), null, interpretation, null));
    String backendId = request.backend() == null ? backends.selected() : request.backend();
    // QB-076: execution only through the central policy, on this caller's tenant.
    caller.require(
        "search",
        io.qwenbridge.core.policy.Resource.of("index", caller.tenant())
            .inDomain("commerce")
            .via(backendId));
    phase = System.nanoTime();
    if (scopes.size() == 1) {
      String scopeKey = plannedScope(caller, backendId, translated, constraints);
      int offset = plannedOffset(request.cursor(), scopeKey);
      var outcome =
          planning.execute(
              caller.tenant(),
              request.backend(),
              scopes.getFirst().ir(),
              reports.getFirst().validated().orElseThrow(),
              translated,
              offset,
              request.limit(),
              request.rerank() ? Optional.of(reranker.asReranker(caller)) : Optional.empty());
      var results = outcome.results();
      String ranking = outcome.retrieval().rankingPolicy() + "+" + outcome.retrieval().reranking();
      stages.put("execution", (System.nanoTime() - phase) / 1_000_000);
      io.qwenbridge.core.context.RequestScope.checkpointCurrent();
      int after = offset + request.limit();
      String next =
          after < Math.min(outcome.found(), CommercePlanning.WINDOW)
              ? plannedCursor(scopeKey, after)
              : null;
      return response(
          request,
          caller,
          id,
          outcome.status(),
          translation,
          a,
          display,
          "OK".equals(outcome.status()) ? List.of() : outcome.retrieval().warnings(),
          results,
          ranking,
          started,
          new Metadata(
              parent,
              next,
              stages,
              trace,
              List.copyOf(locked),
              outcome.retrieval(),
              interpretation,
              null));
    }
    if (request.cursor() != null)
      throw new IllegalArgumentException(
          "A decomposed query is answered per subquery, without paging");
    boolean sameLanguage = translated.equals(request.query());
    List<SubQueryResult> parts = new ArrayList<>();
    for (int i = 0; i < scopes.size(); i++) {
      var scope = scopes.get(i);
      var outcome =
          planning.execute(
              caller.tenant(),
              request.backend(),
              scope.ir(),
              reports.get(i).validated().orElseThrow(),
              scope.objective(),
              0,
              Math.min(request.limit(), CommercePlanning.WINDOW),
              request.rerank() ? Optional.of(reranker.asReranker(caller)) : Optional.empty());
      var retrieval = outcome.retrieval();
      if (!sameLanguage) {
        var warnings = new ArrayList<>(retrieval.warnings());
        warnings.add("SUBQUERY_TEXT_UNTRANSLATED: subquery text is matched as written");
        retrieval =
            new Retrieval(
                retrieval.requested(),
                retrieval.executed(),
                retrieval.reasons(),
                retrieval.degradations(),
                retrieval.degraded(),
                retrieval.removedByValidation(),
                retrieval.providers(),
                warnings,
                retrieval.rankingPolicy(),
                retrieval.reranking(),
                retrieval.outcome());
      }
      parts.add(
          new SubQueryResult(
              scope.id().orElseThrow(),
              scope.objective(),
              outcome.status(),
              scope.commerceConstraints(),
              retrieval,
              outcome.results()));
    }
    stages.put("execution", (System.nanoTime() - phase) / 1_000_000);
    io.qwenbridge.core.context.RequestScope.checkpointCurrent();
    boolean allOk = parts.stream().allMatch(p -> "OK".equals(p.status()));
    return response(
        request,
        caller,
        id,
        allOk ? "OK" : "PARTIAL",
        translation,
        a,
        display,
        List.of(),
        SearchResultSet.empty(),
        "per-subquery",
        started,
        new Metadata(
            parent, null, stages, trace, List.copyOf(locked), null, interpretation, parts));
  }

  private static String plannedScope(
      ApiIdentity.Principal caller, String backend, String text, List<Constraint> constraints) {
    return ApiIdentity.digest(
        SearchRequest.CONTRACT_V1
            + "|"
            + caller.tenant()
            + "|"
            + caller.subject()
            + "|"
            + backend
            + "|"
            + text
            + "|"
            + constraints.stream()
                .map(c -> c.field() + ":" + c.operator() + ":" + c.value())
                .sorted()
                .toList());
  }

  private static final com.fasterxml.jackson.databind.ObjectMapper CURSORS =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private static String plannedCursor(String scope, int offset) {
    try {
      return java.util.Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(CURSORS.writeValueAsBytes(Map.of("scope", scope, "offset", offset)));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static int plannedOffset(String cursor, String scope) {
    if (cursor == null) return 0;
    try {
      var decoded = CURSORS.readTree(java.util.Base64.getUrlDecoder().decode(cursor));
      if (!scope.equals(decoded.path("scope").asText()))
        throw new IllegalArgumentException("Cursor belongs to another query");
      int offset = decoded.path("offset").asInt(-1);
      if (offset < 0) throw new IllegalArgumentException("Invalid cursor");
      return offset;
    } catch (java.io.IOException | IllegalArgumentException ex) {
      if (ex instanceof IllegalArgumentException iae
          && iae.getMessage() != null
          && iae.getMessage().startsWith("Cursor")) throw iae;
      throw new IllegalArgumentException("Invalid cursor", ex);
    }
  }

  private static boolean replaced(Constraint old, List<Constraint> current) {
    return current.stream()
        .filter(c -> c.field().equals(old.field()))
        .anyMatch(
            c ->
                switch (old.operator()) {
                  case EQ -> c.operator() == Operator.EQ;
                  case NE -> c.operator() == Operator.NE && c.value().equals(old.value());
                  case LT, LTE ->
                      Set.of(Operator.EQ, Operator.LT, Operator.LTE).contains(c.operator());
                  case GT, GTE ->
                      Set.of(Operator.EQ, Operator.GT, Operator.GTE).contains(c.operator());
                });
  }

  public SearchResponse executeApproved(
      ApiIdentity.Principal caller,
      SearchResponse previous,
      Plan plan,
      String description,
      String backend) {
    caller.require("search");
    packs.withPack(caller.tenant(), "commerce", p -> p.manifest());
    if (!caller.tenant().equals(plan.tenant())) throw new jakarta.ws.rs.ForbiddenException();
    CommerceCompiler.filters(plan);
    var policy =
        io.qwenbridge.security.Authorization.requireDomainPolicies(
            caller, "commerce", "search", Map.of("query", description, "plan", plan));
    long started = System.nanoTime();
    String id = UUID.randomUUID().toString();
    String translated = CommerceExtraction.render(description, plan.constraints());
    var request =
        new SearchRequest(
            id,
            description,
            "en",
            plan.schemaId(),
            plan.schemaVersion(),
            "KEYWORD",
            List.of(),
            false,
            backend,
            10,
            null,
            previous.lockedFields());
    var page = backends.search(caller, backend, translated, plan, "KEYWORD", List.of(), 10, null);
    // QB-079: carry the model signals recorded for the approved result instead of copying the
    // aggregate confidence into every signal (that reported scores no model ever produced).
    var recorded = previous.confidence().signals();
    double fallback = previous.confidence().value();
    var a =
        SearchAnalysis.builder()
            .language("en")
            .intent(io.qwenbridge.intent.IntentType.PRODUCT_SEARCH)
            .intentConfidence(recorded.getOrDefault("intent", fallback))
            .semanticValidated(true)
            .semanticScore(recorded.getOrDefault("semantic", fallback))
            .decisionConfidence(recorded.getOrDefault("decision", fallback))
            .modelThreatDecision("ALLOW")
            .rewrites(List.of(translated))
            .build();
    var trace =
        policy.decisions().stream()
            .map(t -> new PolicyDecision(t.pack(), t.version(), t.rule(), t.effect(), t.reason()))
            .toList();
    return response(
        request,
        caller,
        id,
        "OK",
        new Translation(1, description, "en", "en", translated, true, List.of()),
        a,
        plan,
        List.of(),
        page.results(),
        "backend-baseline",
        started,
        new Metadata(
            previous.evidence().getFirst().id(),
            page.nextCursor(),
            Map.of("execution", page.results().tookMillis()),
            trace,
            previous.lockedFields()));
  }

  /**
   * Translates through the Core translation stage (QB-015). A failure is an explicit, typed result
   * (QB-016): the text is kept untranslated and the failure becomes a clarification issue, instead
   * of an exception that surfaced as a client error. Preservation is reported by the stage and
   * judged once, on the final text, by {@link CommerceExtraction#translation}.
   */
  private String translate(String text, LanguageTag source, String target, List<String> failures) {
    var to = LanguageTag.parse(target);
    if (to.isEmpty()) {
      failures.add("Translation unavailable");
      return text;
    }
    var outcome =
        translations
            .model()
            .translate(text, source, to, CommerceExtraction.BRANDS, TranslationStage.Policy.REPORT);
    return switch (outcome.result()) {
      case TranslationResult.Translated t -> t.text();
      case TranslationResult.Skipped skipped -> text;
      case TranslationResult.Failed failed -> {
        failures.add("Translation unavailable");
        yield text;
      }
    };
  }

  private SearchResponse response(
      SearchRequest request,
      ApiIdentity.Principal caller,
      String id,
      String status,
      Translation translation,
      SearchAnalysis a,
      Plan plan,
      List<String> questions,
      SearchResultSet results,
      String ranking,
      long started) {
    return response(
        request,
        caller,
        id,
        status,
        translation,
        a,
        plan,
        questions,
        results,
        ranking,
        started,
        new Metadata(null, null, Map.of(), List.of(), request.lockedFields()));
  }

  private SearchResponse response(
      SearchRequest request,
      ApiIdentity.Principal caller,
      String id,
      String status,
      Translation translation,
      SearchAnalysis a,
      Plan plan,
      List<String> questions,
      SearchResultSet results,
      String ranking,
      long started,
      Metadata metadata) {
    var spans =
        plan == null
            ? List.<Evidence>of()
            : plan.constraints().stream().map(Constraint::evidence).toList();
    String parent = metadata.parent();
    var source =
        evidence.recordQuery(
            caller.tenant(),
            id,
            request.query(),
            spans.stream().filter(span -> span.source().equals("original")).toList(),
            parent);
    evidence.bindOwner(caller.tenant(), source.id(), caller.subject());
    var execution =
        evidence.record(
            caller.tenant(),
            (request.backend() == null ? backends.selected() : request.backend())
                    .equals("opensearch")
                ? "opensearch:" + search.properties().index()
                : "urn:qwenbridge:backend:"
                    + (request.backend() == null ? backends.selected() : request.backend()),
            status
                + ":"
                + results.hits().stream()
                    .map(io.qwenbridge.execution.provider.model.SearchHit::id)
                    .toList(),
            List.of(),
            source.id());
    double confidence =
        "OK".equals(status)
            ? Math.min(a.intentConfidence(), Math.min(a.semanticScore(), a.decisionConfidence()))
            : 0;
    var assessment =
        new ConfidenceAssessment(
            confidence,
            "model-signal-min-v1",
            false,
            Map.of(
                "intent",
                a.intentConfidence(),
                "semantic",
                a.semanticScore(),
                "decision",
                a.decisionConfidence()));
    List<String> suggestions =
        a.rewrites().stream()
            .map(q -> CommerceExtraction.render(q, plan == null ? List.of() : plan.constraints()))
            .filter(q -> !q.equals(request.query()))
            .distinct()
            .limit(3)
            .toList();
    var response =
        new SearchResponse(
            2,
            id,
            status,
            translation,
            a.intent().name(),
            plan,
            questions,
            results,
            assessment,
            List.of(source, execution),
            suggestions,
            ranking,
            (System.nanoTime() - started) / 1_000_000,
            metadata.cursor(),
            metadata.timings().isEmpty()
                ? Map.of("total", (System.nanoTime() - started) / 1_000_000)
                : metadata.timings(),
            metadata.policies(),
            metadata.locked(),
            request.contract(),
            metadata.retrieval(),
            metadata.interpretation(),
            metadata.subqueries(),
            metadata.clarifications(),
            null);
    io.qwenbridge.operations.metrics.ApiMetrics.search(
        request.contract(), status, response.tookMs(), response.timings());
    analytics.search(caller, response);
    return response;
  }

  private SearchResponse stopped(
      SearchRequest request,
      ApiIdentity.Principal caller,
      String id,
      String status,
      String message,
      long started) {
    return response(
        request,
        caller,
        id,
        status,
        new Translation(
            1, request.query(), "unknown", request.catalogLanguage(), "", false, List.of(message)),
        SearchAnalysis.fallback(request.query()),
        null,
        List.of(message),
        SearchResultSet.empty(),
        "not-run",
        started);
  }
}
