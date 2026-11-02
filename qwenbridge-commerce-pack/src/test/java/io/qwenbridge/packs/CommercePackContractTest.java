package io.qwenbridge.packs;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.pack.DomainPack;
import io.qwenbridge.pack.PackManifest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** QB-147: the Commerce Domain Pack contract — identity, capabilities, schema and grammar. */
class CommercePackContractTest {
  private final CommercePack pack = new CommercePack();

  @Test
  void manifestDeclaresIdentityVersionCapabilitiesAndNoDependencies() {
    PackManifest m = pack.manifest();
    assertEquals("commerce", m.id());
    assertEquals("1.0.0", m.version());
    assertEquals(1, m.apiVersion());
    assertEquals(PackManifest.Kind.DOMAIN, m.kind());
    assertEquals("qwenbridge", m.publisher());
    assertEquals(Set.of("normalize", "understand", "search"), m.capabilities());
    assertTrue(m.dependencies().isEmpty());
    assertTrue(m.configuration().isEmpty(), "the built-in pack needs no configuration");
  }

  @Test
  void schemaDeclaresTypedFieldsAndOnlyMeaningfulOperators() {
    DomainPack.Definition d = pack.definition();
    assertEquals("commerce", d.id());
    assertEquals(1, d.schemaVersion());
    assertEquals(
        Set.of(
            "brand",
            "color",
            "size",
            "condition",
            "location",
            "category",
            "domain",
            "currency",
            "price",
            "weightKg",
            "inStock",
            "availableAt"),
        d.fields().keySet());
    assertEquals(DomainPack.Type.DECIMAL, d.fields().get("price").type());
    assertEquals(Set.of(DomainPack.Operator.values()), d.fields().get("price").operators());
    assertEquals(
        Set.of(DomainPack.Operator.EQ, DomainPack.Operator.NE),
        d.fields().get("brand").operators());
    assertEquals(Set.of(DomainPack.Operator.EQ), d.fields().get("currency").operators());
    assertTrue(d.fields().get("brand").caseFold());
    assertFalse(d.fields().get("currency").caseFold());
    assertEquals(DomainPack.Type.BOOLEAN, d.fields().get("inStock").type());
    assertEquals(DomainPack.Type.DATE, d.fields().get("availableAt").type());
  }

  private static Map<String, List<String>> clauses(DomainPack.Understanding u) {
    var out = new java.util.TreeMap<String, List<String>>();
    for (var c : u.clauses())
      out.computeIfAbsent(c.field(), k -> new java.util.ArrayList<>())
          .add(c.operator() + " " + c.value());
    return out;
  }

  @Test
  void bundledGrammarIsGroundedAndAsksWhenItCannotInterpret() {
    String q = "black Nike shoes size 42 under 100 EUR not refurbished in stock";
    var u = pack.understand(q);
    assertEquals("commerce", u.domain());
    assertEquals(q, u.original());
    assertEquals(
        Map.of(
            "brand", List.of("EQ Nike"),
            "color", List.of("EQ black"),
            "size", List.of("EQ 42"),
            "price", List.of("LT 100"),
            "currency", List.of("EQ EUR"),
            "condition", List.of("NE refurbished"),
            "category", List.of("EQ shoes"),
            "inStock", List.of("EQ true")),
        clauses(u));
    for (var c : u.clauses()) assertEquals(c.quote(), q.substring(c.start(), c.end()), "grounded");
    assertTrue(u.questions().isEmpty());

    assertEquals(
        List.of("GTE 50"), clauses(pack.understand("laptop at least 50 SEK")).get("price"));
    assertEquals(
        List.of("EQ false"), clauses(pack.understand("mouse out of stock")).get("inStock"));
    assertEquals(List.of("NE Sony"), clauses(pack.understand("headphones not Sony")).get("brand"));

    var unclear = pack.understand("something nice for my aunt");
    assertTrue(unclear.clauses().isEmpty());
    // Nothing understood: it asks for a category, and says the remainder was not interpreted.
    assertEquals(2, unclear.questions().size());
    assertTrue(unclear.questions().getFirst().contains("product category"));
    var partial = pack.understand("Nike shoes for marathon training");
    assertFalse(
        partial.questions().isEmpty(), "uninterpreted remainder is reported, never dropped");
  }

  @Test
  void documentsAreOnePerVariantWithoutTenantAndWithTheDomain() {
    var docs =
        pack.documents(
            "p1",
            Map.of(
                "title",
                "Runner",
                "brand",
                "Nike",
                "currency",
                "EUR",
                "variants",
                List.of(Map.of("id", "a", "price", "10"), Map.of("id", "b", "price", "12"))));
    assertEquals(2, docs.size());
    for (var d : docs) {
      assertEquals("commerce", d.get("domain"));
      assertFalse(d.containsKey("tenantId"), "the platform adds the tenant, never the pack");
    }
    assertEquals(
        Set.of("a", "b"), Set.of(docs.get(0).get("variantId"), docs.get(1).get("variantId")));
    var normalized =
        pack.normalize("p1", Map.of("title", "Runner", "price", "10", "currency", "EUR"));
    assertTrue(normalized.containsKey("product"));
    assertTrue(normalized.containsKey("variants"));
  }
}
