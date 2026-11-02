package io.qwenbridge.core.planning;

import static io.qwenbridge.core.queryir.Constraint.Operator.EQ;
import static io.qwenbridge.core.queryir.Constraint.Operator.GTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.LT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.IrVersion;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.RetrievalMode;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Strategy;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.core.validation.ConstraintPreservationTest;
import io.qwenbridge.core.validation.ConstraintValidator;
import io.qwenbridge.core.validation.DomainMapping;
import io.qwenbridge.core.validation.ValidatedQuery;
import io.qwenbridge.core.validation.ValidationReport;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** QB-099–QB-106. */
class PlanningTest {

  static final String T = "acme";
  static final EmbeddingSpace SPACE = new EmbeddingSpace("m", "1", 4);

  static SearchProvider fake(
      String id, RetrievalMode mode, Set<FilterFeature> features, Optional<EmbeddingSpace> space) {
    return new SearchProvider() {
      public String id() {
        return id;
      }

      public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(EnumSet.of(mode), features, space, 500, true);
      }

      public SearchOutcome search(SearchRequest r, CancellationToken c) {
        return new SearchOutcome.Success(List.of(), 0, Duration.ZERO);
      }
    };
  }

  static QueryIR ir(String text, Set<RetrievalHints.Mode> modes) {
    return new QueryIR(
        IrVersion.CURRENT,
        Optional.empty(),
        text,
        LanguageTag.UNDETERMINED,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new RetrievalHints(modes, false, List.of()),
        List.of(),
        Map.of());
  }

  static Set<RetrievalHints.Mode> modes(String spec) {
    Set<RetrievalHints.Mode> out = EnumSet.noneOf(RetrievalHints.Mode.class);
    for (String m : spec.split("\\+")) {
      if (!m.isBlank()) {
        out.add(RetrievalHints.Mode.valueOf(m));
      }
    }
    return out;
  }

  /**
   * QB-106: the plan-selection matrix. Columns: hints, objective, keyword provider, vector
   * provider, embedding space, max calls, latency budget (ms), expected strategy, expected reason.
   */
  @ParameterizedTest(name = "{0} {1} kw={2} vec={3} emb={4} calls={5} budget={6} -> {7}")
  @CsvSource({
    "LEXICAL,          BALANCED, true,  true,  same,  2, 5000, KEYWORD_ONLY, LEXICAL_NEED",
    "SEMANTIC,         BALANCED, true,  true,  same,  2, 5000, HYBRID,       SEMANTIC_NEED",
    "LEXICAL+SEMANTIC, BALANCED, true,  true,  same,  2, 5000, HYBRID,       BOTH_NEEDS",
    "EXACT+SEMANTIC,   BALANCED, true,  true,  same,  2, 5000, KEYWORD_ONLY, EXACT_IDENTIFIERS",
    "SEMANTIC,         LATENCY,  true,  true,  same,  2, 5000, VECTOR_ONLY,  POLICY_LATENCY",
    "LEXICAL+SEMANTIC, LATENCY,  true,  true,  same,  2, 5000, KEYWORD_ONLY, POLICY_LATENCY",
    "LEXICAL,          QUALITY,  true,  true,  same,  2, 5000, HYBRID,       POLICY_QUALITY",
    "SEMANTIC,         BALANCED, true,  true,  none,  2, 5000, KEYWORD_ONLY, SEMANTIC_UNAVAILABLE",
    "SEMANTIC,         BALANCED, true,  false, same,  2, 5000, KEYWORD_ONLY, SEMANTIC_UNAVAILABLE",
    "SEMANTIC,         BALANCED, true,  true,  other, 2, 5000, KEYWORD_ONLY,"
        + " VECTOR_REINDEX_REQUIRED",
    "SEMANTIC,         BALANCED, true,  true,  same,  1, 5000, VECTOR_ONLY,  BUDGET_LIMITED",
    "LEXICAL+SEMANTIC, BALANCED, true,  true,  same,  2, 100,  KEYWORD_ONLY, BUDGET_LIMITED",
    "SEMANTIC,         BALANCED, false, true,  same,  2, 5000, VECTOR_ONLY,  CAPABILITY_MISMATCH",
    "LEXICAL,          BALANCED, false, true,  same,  2, 5000, VECTOR_ONLY,  CAPABILITY_MISMATCH",
  })
  void planSelectionMatrix(
      String hints,
      PlanningPolicy.Objective objective,
      boolean keyword,
      boolean vector,
      String embedding,
      int calls,
      long budgetMs,
      Strategy expected,
      PlanReason reason) {
    AdaptivePlanner.Providers providers =
        new AdaptivePlanner.Providers(
            keyword
                ? Optional.of(
                    new CandidateProvider(
                        fake(
                            "kw",
                            RetrievalMode.KEYWORD,
                            EnumSet.allOf(FilterFeature.class),
                            Optional.empty()),
                        Duration.ofMillis(50)))
                : Optional.empty(),
            vector
                ? Optional.of(
                    new CandidateProvider(
                        fake(
                            "vec",
                            RetrievalMode.VECTOR,
                            EnumSet.allOf(FilterFeature.class),
                            Optional.of(SPACE)),
                        Duration.ofMillis(80)))
                : Optional.empty(),
            switch (embedding) {
              case "same" -> Optional.of(SPACE);
              case "other" -> Optional.of(new EmbeddingSpace("m", "2", 4));
              default -> Optional.empty();
            },
            Duration.ofMillis(120));
    PlanOutcome outcome =
        AdaptivePlanner.plan(
            T,
            ir("comfortable shoes for long walks", modes(hints)),
            Optional.empty(),
            providers,
            new PlanningPolicy(objective, Duration.ofMillis(budgetMs), calls),
            10);
    RetrievalPlan plan = assertInstanceOf(PlanOutcome.Planned.class, outcome).plan();
    assertEquals(expected, plan.strategy(), plan.reasons().toString());
    assertTrue(plan.reasons().contains(reason), plan.reasons().toString());
    assertEquals(RetrievalPlan.VERSION, plan.version());
    assertEquals(
        RetrievalPlan.Stage.Kind.VALIDATE_HARD_CONSTRAINTS, plan.stages().getLast().kind());
    assertEquals(
        expected != Strategy.KEYWORD_ONLY,
        plan.stages().stream().anyMatch(s -> s.kind() == RetrievalPlan.Stage.Kind.EMBED_QUERY));
  }

  @Test
  void nothingUsableIsATypedUnsupportedOutcome() {
    AdaptivePlanner.Providers onlyVector =
        new AdaptivePlanner.Providers(
            Optional.empty(),
            Optional.of(
                new CandidateProvider(
                    fake(
                        "vec",
                        RetrievalMode.VECTOR,
                        EnumSet.allOf(FilterFeature.class),
                        Optional.of(SPACE)),
                    Duration.ofMillis(10))),
            Optional.of(SPACE),
            Duration.ZERO);
    PlanOutcome exact =
        AdaptivePlanner.plan(
            T,
            ir("SKU-4471", modes("EXACT")),
            Optional.empty(),
            onlyVector,
            PlanningPolicy.DEFAULT,
            10);
    assertTrue(
        assertInstanceOf(PlanOutcome.Unsupported.class, exact)
            .requirements()
            .contains("keyword provider"));
  }

  // --- Real queries: extraction -> validation gate -> compiler -> plan -> execution -------------

  static final DomainSchema SHOP = ConstraintPreservationTest.SHOP;
  static final DomainMapping MAPPING = ConstraintPreservationTest.SHOP_MAPPING;

  static ValidatedQuery validated(String text, String language) {
    Optional<LanguageTag> tag = LanguageTag.parse(language);
    QueryIR ir =
        ConstraintExtractor.extract(
                text,
                new ExtractionContext(
                    tag,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(SHOP),
                    ConstraintPreservationTest.TERMS))
            .applyTo(QueryIR.of(text, tag.orElse(LanguageTag.UNDETERMINED)));
    ValidationReport report = ConstraintValidator.validate(ir, SHOP, MAPPING);
    return report.validated().orElseThrow(() -> new AssertionError(report.toString()));
  }

  static IndexedDocument product(
      String id, String title, String brand, String material, String price) {
    return new IndexedDocument(
        T,
        id,
        title,
        Map.of(
            "brand", new FieldValue.Text(brand),
            "material", new FieldValue.Text(material),
            "price", new FieldValue.Number(new BigDecimal(price)),
            "currency", new FieldValue.Text("SEK")));
  }

  static final List<IndexedDocument> CATALOG =
      List.of(
          product("s1", "Nike running shoes", "Nike", "mesh", "899"),
          product("s2", "Nike leather sneakers", "Nike", "leather", "999"),
          product("s3", "Adidas running shoes", "Adidas", "mesh", "1299"),
          product("s4", "Ecco leather boots", "Ecco", "leather", "1499"),
          product("s5", "Nike trail shoes", "Nike", "synthetic", "1100"));

  @Nested
  class CompilerAndExecution {

    @Test
    void validatedConstraintsCompileToTheNeutralFilterAst() {
      ValidatedQuery q = validated("skor utan läder under 1200 SEK", "sv");
      FilterCompiler.Result.Compiled compiled =
          assertInstanceOf(FilterCompiler.Result.Compiled.class, FilterCompiler.compile(q));
      Filter f = compiled.filter();
      assertTrue(FilterEvaluator.matches(f, CATALOG.get(0).fields()), "mesh 899 SEK");
      assertTrue(!FilterEvaluator.matches(f, CATALOG.get(1).fields()), "leather is excluded");
      assertTrue(!FilterEvaluator.matches(f, CATALOG.get(2).fields()), "1299 is not under 1200");
      assertEquals(
          EnumSet.of(FilterFeature.NOT, FilterFeature.RANGE, FilterFeature.EQUALITY),
          EnumSet.copyOf(f.features()));
    }

    @Test
    void anUncompilableHardConstraintFailsTheWholeQueryNeverDropped() {
      DomainSchema events =
          new DomainSchema(
              "events",
              new SchemaVersion(1, 0),
              Map.of(
                  "startsAt",
                  FieldDefinition.of("startsAt", FieldType.TEMPORAL, Set.of(GTE, LT, EQ))),
              Set.of(),
              Set.of("search"));
      Constraint since =
          new Constraint(
              "c1",
              "startsAt",
              GTE,
              Optional.of(
                  new IrValue.Temporal(IrValue.Temporal.Kind.DATE, "2026-11-01", Optional.empty())),
              Constraint.Strength.HARD,
              Constraint.Provenance.CONTEXT,
              "context",
              Optional.empty(),
              Confidence.deterministic(),
              Optional.empty());
      QueryIR ir = ir("events", Set.of());
      ir =
          new QueryIR(
              ir.version(),
              ir.queryId(),
              ir.originalText(),
              ir.language(),
              ir.intents(),
              ir.entities(),
              List.of(since),
              ir.semanticSignals(),
              ir.retrievalHints(),
              ir.subqueries(),
              ir.extensions());
      ValidatedQuery q =
          ConstraintValidator.validate(ir, events, DomainMapping.none("events"))
              .validated()
              .orElseThrow();
      FilterCompiler.Result r = FilterCompiler.compile(q);
      assertEquals(
          List.of("c1"),
          assertInstanceOf(FilterCompiler.Result.NotCompilable.class, r).constraintIds());
      assertInstanceOf(
          PlanOutcome.Unsupported.class,
          AdaptivePlanner.plan(
              T,
              ir,
              Optional.of(q),
              new AdaptivePlanner.Providers(
                  Optional.of(
                      new CandidateProvider(new InMemoryKeywordIndex("kw", 10, 10), Duration.ZERO)),
                  Optional.empty(),
                  Optional.empty(),
                  Duration.ZERO),
              PlanningPolicy.DEFAULT,
              10));
    }

    @Test
    void plannedQueriesReturnOnlyResultsThatSatisfyEveryHardConstraint() {
      InMemoryKeywordIndex keyword = new InMemoryKeywordIndex("kw", 100, 100);
      CATALOG.forEach(keyword::put);
      ValidatedQuery q = validated("Nike shoes without leather under 1200 SEK", "en");
      RetrievalPlan plan =
          assertInstanceOf(
                  PlanOutcome.Planned.class,
                  AdaptivePlanner.plan(
                      T,
                      q.ir(),
                      Optional.of(q),
                      new AdaptivePlanner.Providers(
                          Optional.of(new CandidateProvider(keyword, Duration.ofMillis(5))),
                          Optional.empty(),
                          Optional.empty(),
                          Duration.ZERO),
                      PlanningPolicy.DEFAULT,
                      10))
              .plan();
      assertEquals(Strategy.KEYWORD_ONLY, plan.strategy());
      assertTrue(plan.text().contains("shoes"), plan.text());
      assertTrue(
          !plan.text().contains("1200"), "compiled constraints leave the text: " + plan.text());
      PlanExecutor.Execution run =
          new PlanExecutor(
                  keyword,
                  Optional.empty(),
                  new EmbeddingStage(Optional.empty(), Duration.ofSeconds(1)))
              .execute(plan);
      List<String> ids = run.result().documents().stream().map(FusedDocument::id).toList();
      assertEquals(List.of("s1", "s5"), ids.stream().sorted().toList());
      assertEquals(0, run.removedByValidation());
    }

    @Test
    void structuredOnlyQueriesBrowseByFilter() {
      InMemoryKeywordIndex keyword = new InMemoryKeywordIndex("kw", 100, 100);
      CATALOG.forEach(keyword::put);
      ValidatedQuery q = validated("under 1000 SEK", "en");
      RetrievalPlan plan =
          ((PlanOutcome.Planned)
                  AdaptivePlanner.plan(
                      T,
                      q.ir(),
                      Optional.of(q),
                      new AdaptivePlanner.Providers(
                          Optional.of(new CandidateProvider(keyword, Duration.ZERO)),
                          Optional.empty(),
                          Optional.empty(),
                          Duration.ZERO),
                      PlanningPolicy.DEFAULT,
                      10))
              .plan();
      assertTrue(plan.reasons().contains(PlanReason.STRUCTURED_ONLY));
      List<String> ids =
          new PlanExecutor(
                  keyword,
                  Optional.empty(),
                  new EmbeddingStage(Optional.empty(), Duration.ofSeconds(1)))
              .execute(plan).result().documents().stream().map(FusedDocument::id).toList();
      assertEquals(List.of("s1", "s2"), ids);
    }

    @Test
    void resultsViolatingTheFilterAreRemovedAndCounted() {
      SearchProvider lying =
          new SearchProvider() {
            public String id() {
              return "lying";
            }

            public ProviderCapabilities capabilities() {
              return new InMemoryKeywordIndex("x", 1, 100).capabilities();
            }

            public SearchOutcome search(SearchRequest r, CancellationToken c) {
              return new SearchOutcome.Success(
                  CATALOG.stream()
                      .map(
                          d ->
                              new ScoredDocument(
                                  d.id(),
                                  CATALOG.indexOf(d) + 1,
                                  new RetrievalScore(1, RetrievalScore.Kind.BM25, "lying"),
                                  d.fields()))
                      .toList(),
                  CATALOG.size(),
                  Duration.ZERO);
            }
          };
      ValidatedQuery q = validated("shoes without leather", "en");
      RetrievalPlan plan =
          ((PlanOutcome.Planned)
                  AdaptivePlanner.plan(
                      T,
                      q.ir(),
                      Optional.of(q),
                      new AdaptivePlanner.Providers(
                          Optional.of(new CandidateProvider(lying, Duration.ZERO)),
                          Optional.empty(),
                          Optional.empty(),
                          Duration.ZERO),
                      PlanningPolicy.DEFAULT,
                      10))
              .plan();
      PlanExecutor.Execution run =
          new PlanExecutor(
                  lying,
                  Optional.empty(),
                  new EmbeddingStage(Optional.empty(), Duration.ofSeconds(1)))
              .execute(plan);
      assertEquals(2, run.removedByValidation(), "the two leather products");
      assertTrue(
          run.result().documents().stream()
              .noneMatch(d -> d.id().equals("s2") || d.id().equals("s4")));
    }

    @Test
    void aProviderThatCannotEvaluateTheFilterIsNotUsed() {
      SearchProvider noNegation =
          fake(
              "limited",
              RetrievalMode.KEYWORD,
              EnumSet.of(FilterFeature.EQUALITY, FilterFeature.RANGE),
              Optional.empty());
      ValidatedQuery q = validated("shoes without leather", "en");
      PlanOutcome outcome =
          AdaptivePlanner.plan(
              T,
              q.ir(),
              Optional.of(q),
              new AdaptivePlanner.Providers(
                  Optional.of(new CandidateProvider(noNegation, Duration.ZERO)),
                  Optional.empty(),
                  Optional.empty(),
                  Duration.ZERO),
              PlanningPolicy.DEFAULT,
              10);
      PlanOutcome.Unsupported u = assertInstanceOf(PlanOutcome.Unsupported.class, outcome);
      assertTrue(
          u.requirements().stream().anyMatch(r -> r.contains("filter NOT")),
          u.requirements().toString());
    }

    @Test
    void hybridPlansExecuteWithVectors() {
      InMemoryKeywordIndex keyword = new InMemoryKeywordIndex("kw", 100, 100);
      CATALOG.forEach(keyword::put);
      EmbeddingProvider fixed =
          new EmbeddingProvider() {
            public String id() {
              return "fixed";
            }

            public EmbeddingModel model() {
              return new EmbeddingModel(SPACE, 32, 1000);
            }

            public EmbeddingResult embed(List<String> texts, Duration timeout) {
              return new EmbeddingResult.Embedded(
                  texts.stream()
                      .map(t -> new double[] {t.length(), 1, t.contains("running") ? 5 : 0, 1})
                      .toList(),
                  SPACE);
            }
          };
      InMemoryVectorIndex vectors = new InMemoryVectorIndex("vec", SPACE, 100, 100);
      for (IndexedDocument d : CATALOG) {
        vectors.put(
            T,
            d.id(),
            ((EmbeddingResult.Embedded) fixed.embed(List.of(d.text()), Duration.ofSeconds(1)))
                .vectors()
                .getFirst(),
            SPACE,
            d.fields());
      }
      ValidatedQuery q = validated("comfortable running shoes under 1200 SEK", "en");
      QueryIR semantic =
          new QueryIR(
              q.ir().version(),
              q.ir().queryId(),
              q.ir().originalText(),
              q.ir().language(),
              q.ir().intents(),
              q.ir().entities(),
              q.ir().constraints(),
              q.ir().semanticSignals(),
              new RetrievalHints(modes("LEXICAL+SEMANTIC"), false, List.of()),
              q.ir().subqueries(),
              q.ir().extensions());
      RetrievalPlan plan =
          ((PlanOutcome.Planned)
                  AdaptivePlanner.plan(
                      T,
                      semantic,
                      Optional.of(q),
                      new AdaptivePlanner.Providers(
                          Optional.of(new CandidateProvider(keyword, Duration.ofMillis(5))),
                          Optional.of(new CandidateProvider(vectors, Duration.ofMillis(5))),
                          Optional.of(SPACE),
                          Duration.ofMillis(5)),
                      PlanningPolicy.DEFAULT,
                      5))
              .plan();
      assertEquals(Strategy.HYBRID, plan.strategy());
      PlanExecutor.Execution run =
          new PlanExecutor(
                  keyword,
                  Optional.of(vectors),
                  new EmbeddingStage(Optional.of(fixed), Duration.ofSeconds(1)))
              .execute(plan);
      assertEquals(Strategy.HYBRID, run.result().executed());
      assertTrue(
          run.result().documents().stream()
              .allMatch(d -> FilterEvaluator.matches(plan.filter(), d.fields())));
      assertTrue(
          run.result().documents().stream()
              .noneMatch(d -> d.id().equals("s3") || d.id().equals("s4")));
    }
  }
}
