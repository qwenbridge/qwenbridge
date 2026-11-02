package io.qwenbridge.core.suggestion;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** QB-132 – QB-137: typed suggestions, grounding, refinement, feedback and determinism. */
class SuggestionTest {

  static Constraint c(String id, String field, Operator op, String value) {
    return new Constraint(
        id,
        field,
        op,
        Optional.of(new IrValue.Text(value)),
        Constraint.Strength.HARD,
        Constraint.Provenance.USER_TEXT,
        "test",
        Optional.empty(),
        Confidence.deterministic(),
        Optional.empty());
  }

  static Suggester.Result r(String id, String brand, String color, String size) {
    return new Suggester.Result(
        id,
        Map.of(
            "brand",
            FieldValue.text(brand),
            "color",
            FieldValue.text(color),
            "size",
            FieldValue.text(size)));
  }

  static List<Suggester.Result> results() {
    return List.of(
        r("a", "Nike", "black", "42"),
        r("b", "Nike", "white", "42"),
        r("c", "Adidas", "black", "42"),
        r("d", "Puma", "black", "42"));
  }

  static final List<Constraint> QUERY =
      List.of(c("m1", "category", Operator.EQ, "shoes"), c("m2", "color", Operator.NE, "red"));

  @Test
  void suggestionsAreGroundedInTheResultsAndTyped() {
    var set =
        Suggester.suggest(
            "req-1", QUERY, results(), List.of("brand", "color", "size"), List.of(), 10);
    assertEquals(SuggestionSet.VERSION, set.version());
    assertEquals("req-1", set.baseRequestId());
    // size: every result is 42 → no narrowing. Nike splits 2/4 exactly: most informative first.
    assertEquals("brand: Nike", set.items().getFirst().text());
    for (Suggestion s : set.items()) {
      assertTrue(s.grounded());
      assertEquals(Suggestion.Source.RESULT_FACETS, s.source());
      assertTrue(s.support() > 0 && s.support() < 4);
      assertEquals(s.support(), Math.min(5, s.evidence().size()));
      assertEquals(1, s.changes().size());
      assertEquals(Change.Op.ADD, s.changes().getFirst().op());
    }
    assertTrue(set.items().stream().noneMatch(s -> s.text().startsWith("size")));
    var nike = set.items().getFirst();
    assertEquals(List.of("a", "b"), nike.evidence());
    // A field the query already fixes is not suggested again; an exclusion does not fix it.
    var fixed =
        Suggester.suggest(
            "req-1",
            List.of(c("m1", "brand", Operator.EQ, "Nike")),
            results(),
            List.of("brand", "color"),
            List.of(),
            10);
    assertTrue(fixed.items().stream().noneMatch(s -> s.text().startsWith("brand")));
    assertTrue(set.items().stream().anyMatch(s -> s.text().startsWith("color")));
  }

  @Test
  void rankingIsDeterministicAndUngroundedRephrasingsComeLast() {
    var expected =
        Suggester.suggest(
            "req",
            QUERY,
            results(),
            List.of("brand", "color"),
            List.of("black trainers", "dark sneakers"),
            10);
    assertEquals(Suggestion.Kind.REPHRASE, expected.items().getLast().kind());
    assertFalse(expected.items().getLast().grounded());
    int firstUngrounded =
        expected
            .items()
            .indexOf(
                expected.items().stream().filter(s -> !s.grounded()).findFirst().orElseThrow());
    assertTrue(
        expected.items().subList(firstUngrounded, expected.items().size()).stream()
            .noneMatch(Suggestion::grounded));
    var shuffled = new ArrayList<>(results());
    for (long seed = 0; seed < 20; seed++) {
      Collections.shuffle(shuffled, new Random(seed));
      var again =
          Suggester.suggest(
              "req",
              QUERY,
              shuffled,
              List.of("brand", "color"),
              List.of("dark sneakers", "black trainers", "dark sneakers"),
              10);
      assertEquals(expected, again);
    }
    for (int i = 0; i < expected.items().size(); i++)
      assertEquals(i + 1, expected.items().get(i).rank());
    assertEquals(
        2,
        Suggester.suggest("req", QUERY, results(), List.of("brand", "color"), List.of(), 2)
            .items()
            .size());
    assertNotEquals(
        expected.items().getFirst().id(),
        Suggester.suggest("other", QUERY, results(), List.of("brand", "color"), List.of(), 1)
            .items()
            .getFirst()
            .id(),
        "ids are scoped to the base request");
  }

  @Test
  void refinementKeepsEveryUnnamedConstraintExactly() {
    Refinement.Builder build =
        (change, i) ->
            new Constraint(
                "r" + i,
                change.field(),
                change.operator(),
                change.value().map(v -> (IrValue) new IrValue.Text(((FieldValue.Text) v).value())),
                Constraint.Strength.HARD,
                Constraint.Provenance.CONTEXT,
                "refinement",
                Optional.empty(),
                Confidence.deterministic(),
                Optional.empty());
    var base =
        List.of(
            c("m1", "category", Operator.EQ, "shoes"),
            c("m2", "color", Operator.NE, "red"),
            c("m3", "brand", Operator.EQ, "Nike"));
    var added =
        Refinement.apply(
            base, List.of(Change.add("size", Operator.EQ, FieldValue.text("42"))), build);
    assertSame(base.get(0), added.get(0));
    assertSame(base.get(1), added.get(1));
    assertSame(base.get(2), added.get(2));
    assertEquals("size", added.get(3).field());
    var replaced =
        Refinement.apply(
            base,
            List.of(
                new Change(
                    Change.Op.REPLACE,
                    "brand",
                    Operator.EQ,
                    Optional.of(FieldValue.text("Adidas")))),
            build);
    assertEquals(3, replaced.size());
    assertEquals("brand", replaced.get(2).field());
    assertEquals(new IrValue.Text("Adidas"), replaced.get(2).value().orElseThrow());
    assertSame(base.get(1), replaced.get(1), "the exclusion survives a replacement elsewhere");
    var removed =
        Refinement.apply(
            base,
            List.of(new Change(Change.Op.REMOVE, "brand", Operator.EQ, Optional.empty())),
            build);
    assertEquals(List.of("m1", "m2"), removed.stream().map(Constraint::id).toList());
    assertThrows(
        IllegalArgumentException.class,
        () -> new Change(Change.Op.ADD, "x", Operator.EQ, Optional.empty()));
  }

  @Test
  void negativeFeedbackBecomesATypedUpdate() {
    Map<String, Map<String, FieldValue>> current =
        Map.of("a", Map.of("price", new FieldValue.Number(new BigDecimal("120"))), "b", Map.of());
    var notRelevant =
        Feedback.interpret(
            new Feedback.Signal(
                Feedback.Kind.NOT_RELEVANT, Optional.of("a"), Optional.empty(), Optional.empty()),
            current);
    assertEquals(List.of("a"), notRelevant.suppressed());
    assertTrue(notRelevant.hard().isEmpty() && notRelevant.preferences().isEmpty());
    var exclude =
        Feedback.interpret(
            new Feedback.Signal(
                Feedback.Kind.EXCLUDE_VALUE,
                Optional.empty(),
                Optional.of("brand"),
                Optional.of(FieldValue.text("Acme"))),
            current);
    assertEquals(Operator.NE, exclude.hard().getFirst().operator());
    assertEquals("brand", exclude.hard().getFirst().field());
    var tooExpensive =
        Feedback.interpret(
            new Feedback.Signal(
                Feedback.Kind.TOO_EXPENSIVE,
                Optional.of("a"),
                Optional.of("price"),
                Optional.empty()),
            current);
    assertTrue(tooExpensive.hard().isEmpty(), "never a filter: by how much is unknown");
    assertEquals(Operator.LT, tooExpensive.preferences().getFirst().operator());
    assertEquals(List.of("a"), tooExpensive.suppressed());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Feedback.interpret(
                new Feedback.Signal(
                    Feedback.Kind.NOT_RELEVANT,
                    Optional.of("zzz"),
                    Optional.empty(),
                    Optional.empty()),
                current));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Feedback.interpret(
                new Feedback.Signal(
                    Feedback.Kind.TOO_EXPENSIVE,
                    Optional.of("b"),
                    Optional.of("price"),
                    Optional.empty()),
                current));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Feedback.Signal(
                Feedback.Kind.EXCLUDE_VALUE,
                Optional.empty(),
                Optional.of("brand"),
                Optional.empty()));
  }
}
