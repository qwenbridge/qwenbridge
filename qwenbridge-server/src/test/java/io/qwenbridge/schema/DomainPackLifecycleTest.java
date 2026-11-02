package io.qwenbridge.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.commerce.CommerceQueryExtension;
import io.qwenbridge.core.policy.Approval;
import io.qwenbridge.core.policy.PolicyDecision;
import io.qwenbridge.core.policy.PolicyEngine;
import io.qwenbridge.core.policy.PolicyRequest;
import io.qwenbridge.core.policy.ReasonCode;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.core.queryir.Extension;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrJson;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.core.schema.SchemaRegistryException;
import io.qwenbridge.core.schema.SchemaVersion;
import io.qwenbridge.domains.DomainPacks;
import io.qwenbridge.pack.DomainPack;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.pack.PolicyPack;
import io.qwenbridge.packs.CommercePack;
import io.qwenbridge.packs.LibraryPack;
import io.qwenbridge.security.ApiIdentity;
import io.qwenbridge.security.Authorization;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-232 – QB-236: Commerce, Library and a minimal test domain use one Domain Pack contract;
 * activation registers schemas, a QueryIR extension namespace and policy rules, and deactivation
 * removes exactly those; extensions coexist without touching the universal QueryIR; domain policies
 * only restrict, through the central engine.
 */
class DomainPackLifecycleTest {

  /** A minimal non-Commerce domain that exercises every contribution. */
  static class RecipesPack implements DomainPack {
    final String packId;
    final String domain;
    final String namespace;
    final List<Rule> rules;

    RecipesPack() {
      this("recipes", "recipes", "recipes", RULES);
    }

    RecipesPack(String packId, String domain, String namespace, List<Rule> rules) {
      this.packId = packId;
      this.domain = domain;
      this.namespace = namespace;
      this.rules = rules;
    }

    static Rule rule(
        String id, java.util.function.Function<PolicyPack.Input, PolicyPack.Effect> f) {
      return new Rule() {
        public String id() {
          return id;
        }

        public PolicyPack.Effect evaluate(PolicyPack.Input input) {
          return f.apply(input);
        }
      };
    }

    static final List<Rule> RULES =
        List.of(
            rule(
                "no-alcohol-for-minors",
                in ->
                    "minor".equals(in.attributes().get("audience"))
                            && String.valueOf(in.attributes().get("query")).contains("wine")
                        ? PolicyPack.Effect.DENY
                        : PolicyPack.Effect.ABSTAIN),
            rule(
                "bulk-import-review",
                in ->
                    in.operation().equals("catalog:write")
                            && "true".equals(in.attributes().get("bulk"))
                        ? PolicyPack.Effect.REVIEW
                        : PolicyPack.Effect.ABSTAIN),
            // A rule that tries to grant everything: it must change nothing.
            rule("always-allow", in -> PolicyPack.Effect.ALLOW),
            rule(
                "fragile",
                in -> {
                  if (in.attributes().containsKey("explode"))
                    throw new IllegalStateException("rule bug");
                  return PolicyPack.Effect.ABSTAIN;
                }));

    public PackManifest manifest() {
      return new PackManifest(
          packId,
          "2.1.0",
          1,
          PackManifest.Kind.DOMAIN,
          "acme",
          List.of(),
          Set.of("understand"),
          Map.of());
    }

    public Definition definition() {
      return new Definition(
          domain,
          2,
          Map.of(
              "cuisine", new Field(Type.TEXT, Set.of(Operator.EQ), "cuisine", true),
              "minutes", new Field(Type.DECIMAL, Set.of(Operator.values()), "minutes", false)));
    }

    public Optional<QueryExtension> queryExtension() {
      return Optional.of(new QueryExtension(namespace, "2.0", Set.of("1.0", "2.0")));
    }

    public List<String> validateExtension(String version, Map<String, Object> data) {
      return data.keySet().equals(Set.of("diet")) && data.get("diet") instanceof String
          ? List.of()
          : List.of("recipes extension must be {diet: string}");
    }

    public List<Rule> policies() {
      return rules;
    }

    public Map<String, Object> normalize(String sourceId, Map<String, Object> input) {
      return Map.of("id", sourceId);
    }

    public Understanding understand(String original) {
      return new Understanding(domain, original, List.of(), List.of());
    }
  }

  static DomainPacks allThree() {
    var packs = new DomainPacks(new SchemaRegistry());
    assertThat(packs.activateAll(List.of(new CommercePack(), new LibraryPack(), new RecipesPack())))
        .isEmpty();
    return packs;
  }

  @Test
  void commerceAndOtherDomainsUseOneContract() {
    var packs = allThree();
    assertThat(packs.registrations())
        .extracting(r -> r.packId() + "@" + r.packVersion() + " " + r.domain() + " " + r.schemas())
        .containsExactly(
            "commerce@1.0.0 commerce [commerce@1.0]",
            "library@1.0.0 library [library@1.0]",
            "recipes@2.1.0 recipes [recipes@2.0]");
    assertThat(packs.schemas().owner("recipes")).contains("pack:recipes");
    assertThat(packs.registrations().get(2).rules())
        .containsExactly(
            "recipes/no-alcohol-for-minors",
            "recipes/bulk-import-review",
            "recipes/always-allow",
            "recipes/fragile");
    assertThat(packs.forDomain("library")).isPresent();
  }

  @Test
  void conflictsAreRefusedWithoutLeavingAnythingBehind() {
    var packs = allThree();
    var before = packs.schemas().keys();
    // Another pack claiming an active domain.
    assertThatThrownBy(
            () -> packs.activate(new RecipesPack("recipes-fork", "recipes", "recipes", List.of())))
        .hasMessageContaining("owned by pack recipes");
    // A namespace that is not the pack's own domain (it would hijack Commerce payloads).
    assertThatThrownBy(
            () -> packs.activate(new RecipesPack("baking", "baking", "commerce", List.of())))
        .hasMessageContaining("must be its domain");
    // Duplicate rule ids.
    var twice = RecipesPack.RULES.getFirst();
    assertThatThrownBy(
            () -> packs.activate(new RecipesPack("menus", "menus", "menus", List.of(twice, twice))))
        .hasMessageContaining("Duplicate policy rule ids");
    // A schema id owned by someone else (here the Core).
    packs
        .schemas()
        .register(
            "core",
            new DomainSchema(
                "drinks", new SchemaVersion(1, 0), Map.of(), Set.of("drink"), Set.of()));
    assertThatThrownBy(
            () -> packs.activate(new RecipesPack("drinks", "drinks", "drinks", List.of())))
        .isInstanceOf(SchemaRegistryException.class);
    assertThatThrownBy(() -> packs.activate(new LibraryPack()))
        .hasMessageContaining("already active");
    var after = new java.util.ArrayList<>(packs.schemas().keys());
    after.removeIf(k -> k.id().equals("drinks"));
    assertThat(after).containsExactlyElementsOf(before);
    assertThat(packs.registrations()).hasSize(3);
    assertThat(packs.rules()).hasSize(4);
  }

  static QueryIR query(Map<String, Extension> extensions) {
    var ir = QueryIR.of("vegan pasta under 30 minutes", LanguageTag.parse("en").orElseThrow());
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        ir.entities(),
        ir.constraints(),
        ir.semanticSignals(),
        ir.retrievalHints(),
        ir.subqueries(),
        extensions);
  }

  @Test
  void extensionsFromSeveralDomainsCoexistWithoutTouchingTheUniversalQueryIr() {
    var packs = allThree();
    var commerce = new CommerceQueryExtension("commerce@1", "en", List.of("EUR")).toExtension();
    var mixed =
        query(
            Map.of(
                "commerce", commerce,
                "library", new Extension("1.0", Map.of("language", "sv")),
                "recipes", new Extension("1.0", Map.of("diet", "vegan"))));
    assertThat(packs.validate(mixed)).isEmpty();
    // The universal JSON contract carries them unchanged.
    var read = QueryIrJson.read(QueryIrJson.write(mixed));
    assertThat(read).isInstanceOf(QueryIrJson.Result.Valid.class);
    var back = ((QueryIrJson.Result.Valid) read).ir();
    assertThat(back.extensions().keySet())
        .containsExactlyInAnyOrder("commerce", "library", "recipes");
    assertThat(packs.validate(back)).isEmpty();
    assertThat(back.originalText()).isEqualTo(mixed.originalText());

    var broken =
        query(
            Map.of(
                "commerce", new Extension("2.0", commerce.data()),
                "library", new Extension("1.0", Map.of("shelf", "B4")),
                "recipes", new Extension("3.0", Map.of("diet", "vegan")),
                "weather", new Extension("1.0", Map.of("city", "Oslo"))));
    assertThat(packs.validate(broken))
        .containsExactly(
            "commerce: incompatible extension version 2.0 (readable: [1.0])",
            "library: unknown library extension key shelf",
            "recipes: incompatible extension version 3.0 (readable: [1.0, 2.0])",
            "weather: no active domain pack owns this extension");
    assertThat(
            packs.validate(
                query(Map.of("commerce", new Extension("1.0", Map.of("schema", "commerce@1"))))))
        .first()
        .asString()
        .startsWith("commerce: commerce extension keys must be");
  }

  static final ApiIdentity.Principal COOK =
      new ApiIdentity.Principal("cook", "acme", Set.of("search", "catalog:write"));

  static PolicyDecision decide(
      PolicyEngine engine,
      ApiIdentity.Principal who,
      String op,
      String domain,
      Map<String, String> context) {
    return engine.decide(
        new PolicyRequest(
            Authorization.subject(who),
            op,
            Resource.of(op.equals("search") ? "index" : "catalog", "acme").inDomain(domain),
            context,
            Optional.<Approval>empty()));
  }

  @Test
  void domainPoliciesOnlyRestrictThroughTheCentralEngine() {
    var packs = allThree();
    var engine =
        new PolicyEngine(
            Authorization.CATALOG, packs::rules, Clock.systemUTC(), true, Duration.ofHours(1));
    // An ALLOW rule never grants: without the scope the base check denies.
    var reader = new ApiIdentity.Principal("guest", "acme", Set.of("search"));
    assertThat(decide(engine, reader, "catalog:write", "recipes", Map.of()).reasons())
        .containsExactly(ReasonCode.NO_SCOPE);
    // Nor can it lift the tenant boundary.
    var outsider = new ApiIdentity.Principal("eve", "globex", Set.of("search"));
    assertThat(decide(engine, outsider, "search", "recipes", Map.of()).reasons())
        .containsExactly(ReasonCode.TENANT_MISMATCH);
    assertThat(decide(engine, COOK, "search", "recipes", Map.of("query", "pasta")).allowed())
        .isTrue();

    var denied =
        decide(
            engine,
            COOK,
            "search",
            "recipes",
            Map.of("audience", "minor", "query", "wine pairing"));
    assertThat(denied.reasons()).containsExactly(ReasonCode.POLICY_DENY);
    assertThat(denied.policyIds()).containsExactly("recipes/no-alcohol-for-minors");
    var review = decide(engine, COOK, "catalog:write", "recipes", Map.of("bulk", "true"));
    assertThat(review.outcome()).isEqualTo(PolicyDecision.Outcome.REQUIRES_APPROVAL);
    assertThat(review.policyIds()).containsExactly("recipes/bulk-import-review");
    var failing = decide(engine, COOK, "search", "recipes", Map.of("explode", "1"));
    assertThat(failing.reasons()).containsExactly(ReasonCode.POLICY_ERROR);
    assertThat(failing.policyIds()).containsExactly("recipes/fragile");
    // The same request in another domain is not affected by the recipes rules.
    assertThat(
            decide(
                    engine,
                    COOK,
                    "search",
                    "library",
                    Map.of("audience", "minor", "query", "wine", "explode", "1"))
                .allowed())
        .isTrue();
  }

  @Test
  void disablingCommerceLeavesOtherDomainsWorking() {
    var packs = allThree();
    var engine =
        new PolicyEngine(
            Authorization.CATALOG, packs::rules, Clock.systemUTC(), true, Duration.ofHours(1));
    var removed = packs.deactivate("commerce").orElseThrow();
    assertThat(removed.schemas()).extracting(Object::toString).containsExactly("commerce@1.0");
    assertThat(packs.schemas().latest("commerce")).isEmpty();
    assertThat(packs.schemas().latest("library")).isPresent();
    assertThat(packs.schemas().latest("recipes")).isPresent();
    assertThat(
            packs.validate(
                query(Map.of("library", new Extension("1.0", Map.of("edition", "2nd"))))))
        .isEmpty();
    assertThat(
            packs.validate(
                query(
                    Map.of(
                        "commerce",
                        new CommerceQueryExtension("commerce@1", "en", List.of()).toExtension()))))
        .containsExactly("commerce: no active domain pack owns this extension");
    assertThat(
            decide(engine, COOK, "search", "recipes", Map.of("audience", "minor", "query", "wine"))
                .allowed())
        .isFalse();
    assertThat(packs.deactivate("commerce")).isEmpty();
    // Deactivating a domain removes only its own rules and schemas; it can come back.
    packs.deactivate("recipes");
    assertThat(packs.rules()).isEmpty();
    assertThat(
            decide(engine, COOK, "search", "recipes", Map.of("audience", "minor", "query", "wine"))
                .allowed())
        .isTrue();
    packs.activate(new CommercePack());
    assertThat(packs.schemas().latest("commerce")).isPresent();
    assertThat(packs.registrations())
        .extracting(DomainPacks.Registration::packId)
        .containsExactly("library", "commerce");
  }

  @Test
  void theServerStartsWithAnySubsetOfDomainPacks() {
    var withoutCommerce = SchemaRegistryProducer.domainPacks(List.of("commerce"));
    assertThat(withoutCommerce.registrations())
        .extracting(DomainPacks.Registration::packId)
        .containsExactly("library");
    assertThat(withoutCommerce.schemas().latest("commerce")).isEmpty();
    var none = SchemaRegistryProducer.domainPacks(List.of("commerce", "library"));
    assertThat(none.registrations()).isEmpty();
    assertThat(none.schemas().keys()).isEmpty();
    assertThat(none.rules()).isEmpty();
  }

  @Test
  void commercePackAcceptsExactlyThePayloadTheServerWrites() {
    var pack = new CommercePack();
    var written =
        new CommerceQueryExtension("commerce@3", "sv", List.of("SEK", "EUR")).toExtension();
    assertThat(pack.validateExtension(written.version(), written.data())).isEmpty();
    assertThat(pack.queryExtension().orElseThrow().version())
        .isEqualTo(CommerceQueryExtension.VERSION);
    assertThat(
            pack.validateExtension(
                "1.0",
                Map.of(
                    "schema",
                    "x",
                    "catalogLanguage",
                    "en",
                    "currencies",
                    List.of(new BigDecimal("1")))))
        .contains(
            "schema must be a schema reference id@version",
            "currencies must be a list of ISO 4217 codes");
  }
}
