package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.Grounded.Constraint;
import io.qwenbridge.analysis.model.Grounded.Evidence;
import io.qwenbridge.analysis.model.Grounded.Operator;
import io.qwenbridge.catalog.CatalogNormalizer;
import io.qwenbridge.connectors.*;
import io.qwenbridge.core.planning.PlanningPolicy;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.platform.Entitlements;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * QB-086 – QB-090 without a server: Commerce constraints interpreted over the universal QueryIR,
 * validated against the Commerce schema and executed by the Core planner on the in-memory backend
 * with real vectors from a deterministic character-trigram embedder (test fixture only).
 */
class CommercePlanningTest {
  static final int DIMENSIONS = 128;

  /** Deterministic test embedder: hashed character trigrams, so related wording shares vectors. */
  static List<Double> embed(String text) {
    double[] v = new double[DIMENSIONS];
    String t = " " + text.toLowerCase(Locale.ROOT) + " ";
    for (int i = 0; i + 3 <= t.length(); i++) {
      int h = java.util.Arrays.hashCode(t.substring(i, i + 3).getBytes(StandardCharsets.UTF_8));
      v[Math.floorMod(h, DIMENSIONS)] += 1;
    }
    var out = new ArrayList<Double>(DIMENSIONS);
    for (double d : v) out.add(d);
    return out;
  }

  static final Map<String, Map<String, Object>> CATALOG = new java.util.LinkedHashMap<>();

  static {
    CATALOG.put("p1", product("Cushioned comfortable running shoes", "Nike", "black", "89", "new"));
    CATALOG.put("p2", product("Racing spikes", "Nike", "black", "120", "new"));
    CATALOG.put("p3", product("Plush comfort trainers", "Nike", "black", "70", "refurbished"));
    CATALOG.put("p4", product("Trail running shoes", "Nike", "black", "95", null));
    CATALOG.put("p5", product("Hiking backpack", "Osprey", "white", "60", "new"));
    CATALOG.put("p6", product("Running shoes", "Adidas", "white", "80", "new"));
  }

  static Map<String, Object> product(
      String title, String brand, String color, String price, String condition) {
    var raw = new HashMap<String, Object>();
    raw.put("title", title);
    raw.put("brand", brand);
    raw.put("color", color);
    raw.put("price", price);
    raw.put("currency", "EUR");
    raw.put("inStock", true);
    if (condition != null) raw.put("condition", condition);
    return raw;
  }

  static CommercePlanning planning(boolean embeddingsUp, boolean vectors) {
    var ai = mock(AIService.class);
    if (embeddingsUp)
      when(ai.embed(any()))
          .thenAnswer(
              i -> new EmbeddingResponse(embed(((EmbeddingRequest) i.getArgument(0)).text())));
    else when(ai.embed(any())).thenThrow(new IllegalStateException("embedding service down"));
    var stages =
        new EmbeddingStages(ai, true, "trigram-test", "1", DIMENSIONS, Duration.ofSeconds(5));
    var memory =
        vectors ? new MemoryCommerceBackend(stages.stage().space()) : new MemoryCommerceBackend();
    var normalizer = new CatalogNormalizer();
    CATALOG.forEach(
        (id, raw) -> {
          var product = normalizer.normalize("http-json", id, raw);
          var doc =
              new HashMap<>(normalizer.document(product, product.variants().getFirst(), "t1"));
          doc.put("embedding", embed(product.title() + " " + product.brand()));
          memory.upsert("t1", id, doc);
        });
    memory.upsert(
        "t2",
        "x1",
        new HashMap<>(
            Map.of(
                "title",
                "Comfortable running shoes",
                "brand",
                "Nike",
                "color",
                "black",
                "price",
                1,
                "currency",
                "EUR")));
    var os = mock(OpenSearchBackend.class);
    when(os.id()).thenReturn("opensearch");
    var planning = new CommercePlanning();
    planning.backends =
        new CommerceBackends(os, memory, mock(Entitlements.class), new ObjectMapper(), "memory");
    planning.memory = memory;
    planning.opensearch = os;
    planning.embeddings = stages;
    planning.objective = PlanningPolicy.Objective.BALANCED;
    planning.timeout = Duration.ofSeconds(5);
    planning.keywordLatency = Duration.ofMillis(50);
    planning.vectorLatency = Duration.ofMillis(100);
    planning.embeddingLatency = Duration.ofMillis(500);
    planning.rankingPolicies =
        new io.qwenbridge.ranking.policy.RankingPolicies(
            "qwenbridge-baseline", "1.0", 1.0, 0.5, 0.0, 20, Optional.empty());
    planning.rerankTimeoutMs = 1000;
    return planning;
  }

  static Constraint c(String query, String field, Operator op, String value, String quote) {
    int start = query.indexOf(quote);
    assertThat(start).as(quote).isGreaterThanOrEqualTo(0);
    return new Constraint(
        field, op, value, new Evidence("original", start, start + quote.length(), quote));
  }

  record Run(CommercePlanning.Outcome outcome, List<String> ids) {}

  static Run run(CommercePlanning planning, String query, List<Constraint> constraints) {
    var ir = CommerceInterpretation.interpret(query, LanguageTag.parse("en"), constraints);
    var scopes = CommerceInterpretation.scopes(ir, constraints);
    assertThat(scopes).hasSize(1);
    var report = CommerceInterpretation.validate(scopes.getFirst().ir());
    assertThat(report.validated()).as(report.assessments().toString()).isPresent();
    var outcome =
        planning.execute(
            "t1",
            null,
            scopes.getFirst().ir(),
            report.validated().get(),
            query,
            0,
            10,
            Optional.empty());
    return new Run(outcome, outcome.results().hits().stream().map(SearchHit::id).toList());
  }

  @Test
  void structuredOnlyQueriesBrowseByFilterWithoutEmbedding() {
    String q = "Nike under 100 EUR";
    var run =
        run(
            planning(true, true),
            q,
            List.of(
                c(q, "brand", Operator.EQ, "nike", "Nike"),
                c(q, "price", Operator.LT, "100", "under 100 EUR"),
                c(q, "currency", Operator.EQ, "eur", "EUR")));
    assertThat(run.outcome().retrieval().reasons()).contains("STRUCTURED_ONLY");
    assertThat(run.outcome().retrieval().executed()).isEqualTo("KEYWORD_ONLY");
    assertThat(run.outcome().retrieval().degraded()).isFalse();
    // Canonical values: "nike" and "eur" match the catalog's "Nike" and "EUR"; never the other
    // tenant's cheaper product.
    assertThat(run.ids()).containsExactlyInAnyOrder("p1", "p3", "p4");
  }

  @Test
  void subjectiveWordsAreSignalsThatAskForMeaningButNeverFilter() {
    String q = "comfortable Nike shoes";
    var ir =
        CommerceInterpretation.interpret(
            q, LanguageTag.parse("en"), List.of(c(q, "brand", Operator.EQ, "Nike", "Nike")));
    assertThat(ir.semanticSignals()).extracting(s -> s.concept()).containsExactly("comfort");
    assertThat(ir.hardConstraints()).extracting(x -> x.field()).containsExactly("brand");
    var run = run(planning(true, true), q, List.of(c(q, "brand", Operator.EQ, "Nike", "Nike")));
    assertThat(run.outcome().retrieval().requested()).isEqualTo("HYBRID");
    assertThat(run.outcome().retrieval().executed()).isEqualTo("HYBRID");
    assertThat(run.outcome().retrieval().providers()).containsExactly("memory", "memory-vector");
    assertThat(run.outcome().retrieval().degraded()).isFalse();
    // Every result satisfies the HARD filter; "comfortable" only ranked.
    assertThat(run.outcome().results().hits())
        .allSatisfy(h -> assertThat(h.document()).containsEntry("brand", "Nike"));
    assertThat(run.ids()).contains("p1", "p3").doesNotContain("p6", "p5");
  }

  @Test
  void unavailableSemanticsDegradeVisiblyToKeyword() {
    String q = "comfortable Nike shoes";
    var constraints = List.of(c(q, "brand", Operator.EQ, "Nike", "Nike"));
    var down = run(planning(false, true), q, constraints);
    assertThat(down.outcome().retrieval().requested()).isEqualTo("HYBRID");
    assertThat(down.outcome().retrieval().executed()).isEqualTo("KEYWORD_ONLY");
    assertThat(down.outcome().retrieval().degraded()).isTrue();
    assertThat(down.outcome().retrieval().degradations())
        .anySatisfy(d -> assertThat(d).contains("EMBEDDING_UNAVAILABLE"));
    assertThat(down.ids()).isNotEmpty();

    var noVectors = run(planning(true, false), q, constraints);
    assertThat(noVectors.outcome().retrieval().reasons()).contains("SEMANTIC_UNAVAILABLE");
    assertThat(noVectors.outcome().retrieval().requested()).isEqualTo("HYBRID");
    assertThat(noVectors.outcome().retrieval().executed()).isEqualTo("KEYWORD_ONLY");
    assertThat(noVectors.outcome().retrieval().degraded()).isTrue();
  }

  @Test
  void exclusionsUseTheNeutralSemanticsAMissingFieldPasses() {
    String q = "Nike shoes not refurbished";
    var run =
        run(
            planning(true, true),
            q,
            List.of(
                c(q, "brand", Operator.EQ, "Nike", "Nike"),
                c(q, "condition", Operator.NE, "refurbished", "not refurbished")));
    // p4 states no condition: kept (contract 1.0). p3 is refurbished: excluded.
    assertThat(run.ids()).contains("p4").doesNotContain("p3");
  }

  @Test
  void multiProductQueriesAreScopedByWhereEachConstraintWasStated() {
    String q = "black Nike shoes and a white backpack under 100 EUR";
    var constraints =
        List.of(
            c(q, "color", Operator.EQ, "black", "black"),
            c(q, "brand", Operator.EQ, "Nike", "Nike"),
            c(q, "color", Operator.EQ, "white", "white"),
            c(q, "price", Operator.LT, "100", "under 100 EUR"),
            c(q, "currency", Operator.EQ, "EUR", "EUR"));
    var ir = CommerceInterpretation.interpret(q, LanguageTag.parse("en"), constraints);
    var scopes = CommerceInterpretation.scopes(ir, constraints);
    assertThat(scopes).hasSize(2);
    assertThat(scopes.get(0).objective()).isEqualTo("black Nike shoes");
    assertThat(scopes.get(0).commerceConstraints())
        .extracting(Constraint::field)
        .containsExactly("color", "brand");
    assertThat(scopes.get(1).commerceConstraints())
        .extracting(Constraint::field)
        .containsExactly("color", "price", "currency");
    var planning = planning(true, true);
    var shoes = CommerceInterpretation.validate(scopes.get(0).ir()).validated().orElseThrow();
    var bags = CommerceInterpretation.validate(scopes.get(1).ir()).validated().orElseThrow();
    var first =
        planning.execute(
            "t1",
            null,
            scopes.get(0).ir(),
            shoes,
            scopes.get(0).objective(),
            0,
            10,
            Optional.empty());
    var second =
        planning.execute(
            "t1",
            null,
            scopes.get(1).ir(),
            bags,
            scopes.get(1).objective(),
            0,
            10,
            Optional.empty());
    assertThat(first.results().hits())
        .isNotEmpty()
        .allSatisfy(h -> assertThat(h.document()).containsEntry("color", "black"));
    assertThat(second.results().hits()).extracting(SearchHit::id).contains("p5");
    assertThat(second.results().hits())
        .allSatisfy(h -> assertThat(h.document()).containsEntry("color", "white"));
  }

  @Test
  void dateConstraintsAreRefusedAsUnsupportedNeverDropped() {
    String q = "Nike available from 2026-10-01";
    var constraints =
        List.of(
            c(q, "brand", Operator.EQ, "Nike", "Nike"),
            c(q, "availableAt", Operator.GTE, "2026-10-01", "from 2026-10-01"));
    var ir = CommerceInterpretation.interpret(q, LanguageTag.parse("en"), constraints);
    var report = CommerceInterpretation.validate(ir);
    assertThat(report.validated()).isPresent();
    var outcome =
        planning(true, true)
            .execute("t1", null, ir, report.validated().get(), q, 0, 10, Optional.empty());
    assertThat(outcome.status()).isEqualTo("UNSUPPORTED");
    assertThat(outcome.results().hits()).isEmpty();
    assertThat(outcome.retrieval().degraded()).isTrue();
    assertThat(outcome.retrieval().warnings()).isNotEmpty();
  }

  @Test
  void aFailedProviderIsReportedAsUnavailableNeverAsAnEmptyCatalog() {
    var planning = planning(true, false);
    var failing = mock(MemoryCommerceBackend.class);
    when(failing.id()).thenReturn("memory");
    when(failing.capabilities()).thenReturn(new MemoryCommerceBackend().capabilities());
    when(failing.search(
            any(io.qwenbridge.core.retrieval.SearchRequest.class),
            any(io.qwenbridge.core.retrieval.CancellationToken.class)))
        .thenReturn(
            new io.qwenbridge.core.retrieval.SearchOutcome.Failed(
                io.qwenbridge.core.retrieval.SearchOutcome.Failure.UNAVAILABLE, "down"));
    when(failing.vector()).thenReturn(Optional.empty());
    var os = mock(OpenSearchBackend.class);
    when(os.id()).thenReturn("opensearch");
    planning.backends =
        new CommerceBackends(os, failing, mock(Entitlements.class), new ObjectMapper(), "memory");
    planning.memory = failing;
    String q = "Nike shoes";
    var constraints = List.of(c(q, "brand", Operator.EQ, "Nike", "Nike"));
    var ir = CommerceInterpretation.interpret(q, LanguageTag.parse("en"), constraints);
    var validated = CommerceInterpretation.validate(ir).validated().orElseThrow();
    var outcome = planning.execute("t1", null, ir, validated, q, 0, 10, Optional.empty());
    assertThat(outcome.status()).isEqualTo("UNAVAILABLE");
    assertThat(outcome.retrieval().outcome()).isEqualTo("PROVIDER_FAILED");
    assertThat(outcome.retrieval().degraded()).isTrue();
    assertThat(outcome.results().hits()).isEmpty();
    var ok = run(planning(true, true), q, constraints);
    assertThat(ok.outcome().retrieval().outcome()).isEqualTo("RESULTS");
  }

  @Test
  void theQueryIrCarriesATypedCommerceExtension() {
    String q = "Nike under 100 sek";
    var constraints =
        List.of(
            c(q, "brand", Operator.EQ, "Nike", "Nike"),
            c(q, "price", Operator.LT, "100", "under 100"),
            c(q, "currency", Operator.EQ, "sek", "sek"));
    var ir = CommerceInterpretation.interpret(q, LanguageTag.parse("en"), constraints, "sv");
    var ext = ir.extensions().get(CommerceQueryExtension.NAMESPACE);
    assertThat(ext.version()).isEqualTo("1.0");
    var typed = CommerceQueryExtension.from(ext);
    assertThat(typed).isEqualTo(new CommerceQueryExtension("commerce@1", "sv", List.of("SEK")));
    assertThat(io.qwenbridge.core.queryir.QueryIrValidator.validate(ir)).isEmpty();
    // It survives the strict QueryIR JSON round trip; Core never interprets it.
    var read =
        io.qwenbridge.core.queryir.QueryIrJson.read(
            io.qwenbridge.core.queryir.QueryIrJson.write(ir));
    assertThat(read).isInstanceOf(io.qwenbridge.core.queryir.QueryIrJson.Result.Valid.class);
    var back = ((io.qwenbridge.core.queryir.QueryIrJson.Result.Valid) read).ir();
    assertThat(CommerceQueryExtension.from(back.extensions().get("commerce"))).isEqualTo(typed);
    assertThatThrownBy(
            () ->
                CommerceQueryExtension.from(
                    new io.qwenbridge.core.queryir.Extension("2.0", ext.data())))
        .hasMessageContaining("unsupported");
    var extra = new java.util.HashMap<String, Object>(ext.data());
    extra.put("price", "1");
    assertThatThrownBy(
            () ->
                CommerceQueryExtension.from(new io.qwenbridge.core.queryir.Extension("1.0", extra)))
        .hasMessageContaining("keys");
    assertThatThrownBy(() -> new CommerceQueryExtension("commerce@1", "sv", List.of("sek")))
        .hasMessageContaining("currency");
  }

  @Test
  void canonicalValuesAndPageWindow() {
    assertThat(CommerceInterpretation.canonicalValue("brand", "nike")).isEqualTo("Nike");
    assertThat(CommerceInterpretation.canonicalValue("color", "Noir")).isEqualTo("black");
    assertThat(CommerceInterpretation.canonicalValue("category", "Sneakers")).isEqualTo("shoes");
    assertThat(CommerceInterpretation.canonicalValue("currency", "sek")).isEqualTo("SEK");
    assertThat(CommerceInterpretation.canonicalValue("size", "42")).isEqualTo("42");
    String q = "Nike";
    var constraints = List.of(c(q, "brand", Operator.EQ, "Nike", "Nike"));
    var ir = CommerceInterpretation.interpret(q, Optional.empty(), constraints);
    var validated = CommerceInterpretation.validate(ir).validated().orElseThrow();
    var planning = planning(true, true);
    assertThatThrownBy(
            () -> planning.execute("t1", null, ir, validated, q, 25, 10, Optional.empty()))
        .hasMessageContaining("at most 30");
    var page = planning.execute("t1", null, ir, validated, q, 1, 2, Optional.empty());
    assertThat(page.results().hits()).hasSize(2);
    assertThat(page.found()).isEqualTo(4);
  }
}
