package io.qwenbridge.core.queryir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** QB-036/037/040: the contract's shape is domain- and backend-neutral and versioned. */
class QueryIrContractTest {

  @Test
  void topLevelFieldsAreExactlyTheDomainNeutralSet() {
    List<String> fields =
        Arrays.stream(QueryIR.class.getRecordComponents()).map(RecordComponent::getName).toList();
    assertEquals(
        List.of(
            "version",
            "queryId",
            "originalText",
            "language",
            "intents",
            "entities",
            "constraints",
            "semanticSignals",
            "retrievalHints",
            "subqueries",
            "extensions",
            // QueryIR 1.1 (QB-291): optional typo correction metadata; 1.0 documents are unchanged.
            "correction",
            // QueryIR 1.1 (QB-286): optional multilingual expansion metadata, same minor version
            // (1.1 is unreleased); 1.0 documents are unchanged.
            "expansion"),
        fields,
        "changing the QueryIR shape requires a version decision (see IrVersion)");
  }

  @Test
  void noComponentNameMentionsADomainOrBackend() {
    List<String> forbidden =
        List.of(
            "price",
            "product",
            "sku",
            "brand",
            "currency",
            "commerce",
            "shopify",
            "pod",
            "kubernetes",
            "opensearch",
            "elastic",
            "sql",
            "mongo",
            "redis",
            "dsl");
    for (Class<?> type :
        List.of(
            QueryIR.class,
            Intent.class,
            Entity.class,
            Constraint.class,
            SemanticSignal.class,
            RetrievalHints.class,
            SubQuery.class,
            Span.class,
            Extension.class)) {
      for (RecordComponent component : type.getRecordComponents()) {
        String name = component.getName().toLowerCase(Locale.ROOT);
        forbidden.forEach(
            word ->
                assertFalse(name.contains(word), type.getSimpleName() + "." + component.getName()));
      }
    }
  }

  @Test
  void retrievalHintsHaveNoBackendSyntax() {
    for (RetrievalHints.Mode mode : RetrievalHints.Mode.values()) {
      assertFalse(mode.name().contains("KNN") || mode.name().contains("BM25"), mode.name());
    }
  }

  @Test
  void versionCompatibilityRules() {
    IrVersion reader = new IrVersion(1, 2);
    assertTrue(reader.canRead(new IrVersion(1, 0)));
    assertTrue(reader.canRead(new IrVersion(1, 2)));
    assertFalse(reader.canRead(new IrVersion(1, 3)), "newer minor may carry unknown semantics");
    assertFalse(reader.canRead(new IrVersion(2, 0)), "major change is breaking");
    assertFalse(reader.canRead(new IrVersion(0, 9)));
    assertEquals("1.0", IrVersion.CURRENT.toString());
  }

  @Test
  void negativeOperatorsAreClassifiedExplicitly() {
    for (Constraint.Operator op : Constraint.Operator.values()) {
      boolean expected = op.name().startsWith("NOT_") || op == Constraint.Operator.NE;
      assertEquals(expected, op.negative(), op.name());
    }
  }

  @Test
  void structuralInvariantsAreEnforcedOnConstruction() {
    assertThrows(IllegalArgumentException.class, () -> new Span(3, 1, ""));
    assertThrows(IllegalArgumentException.class, () -> new Span(0, 2, "abc"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new IrValue.Money(java.math.BigDecimal.ONE, java.util.Optional.of("usd")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrValue.Range(java.util.Optional.empty(), java.util.Optional.empty(), true, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Extension("1.0", java.util.Map.of("bad", new Object())));
  }

  @Test
  void extensionDataIsDeeplyImmutable() {
    java.util.Map<String, Object> inner = new java.util.HashMap<>();
    inner.put("k", "v");
    Extension extension = new Extension("1.0", java.util.Map.of("inner", inner));
    inner.put("k", "changed");
    assertEquals("v", ((java.util.Map<?, ?>) extension.data().get("inner")).get("k"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> ((java.util.Map<String, Object>) extension.data().get("inner")).put("x", "y"));
  }
}
