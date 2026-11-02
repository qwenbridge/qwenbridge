package io.qwenbridge.core.suggestion;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Grounded suggestions and their deterministic ranking (QB-134, QB-137).
 *
 * <p>Refinements come from the current results only: for each facet field the query does not
 * already fix, every value present in some but not all results becomes a suggestion to narrow to
 * it. Its support is the number of results that have the value, so a grounded suggestion can never
 * lead to an empty result within what was returned. Rephrasings from a model are kept, labelled
 * ungrounded, and ranked last.
 *
 * <p>Ranking is deterministic: grounded before ungrounded; then the most even split (support
 * closest to half the results, the most informative narrowing); then facet order; then value. The
 * same inputs always give the same ids and order.
 */
public final class Suggester {

  /** One result: its id and typed fields. */
  public record Result(String id, Map<String, FieldValue> fields) {
    public Result {
      fields = Map.copyOf(fields);
    }
  }

  private Suggester() {}

  public static SuggestionSet suggest(
      String baseRequestId,
      List<Constraint> constraints,
      List<Result> results,
      List<String> facets,
      List<String> rephrasings,
      int limit) {
    if (limit < 0 || limit > 50) {
      throw new IllegalArgumentException("limit must be within [0, 50]");
    }
    Set<String> fixed =
        constraints.stream()
            .filter(c -> c.operator() == Operator.EQ && !c.negative())
            .map(Constraint::field)
            .collect(java.util.stream.Collectors.toSet());
    record Candidate(Suggestion suggestion, int facetOrder, String value, double balance) {}
    List<Candidate> candidates = new ArrayList<>();
    int total = results.size();
    for (int f = 0; f < facets.size(); f++) {
      String field = facets.get(f);
      if (fixed.contains(field)) {
        continue;
      }
      TreeMap<String, List<String>> byValue = new TreeMap<>();
      Map<String, FieldValue> typed = new TreeMap<>();
      for (Result r : results) {
        FieldValue v = r.fields().get(field);
        if (v instanceof FieldValue.Text
            || v instanceof FieldValue.Number
            || v instanceof FieldValue.Bool) {
          String key = display(v);
          byValue.computeIfAbsent(key, k -> new ArrayList<>()).add(r.id());
          typed.putIfAbsent(key, v);
        }
      }
      for (var entry : byValue.entrySet()) {
        int support = entry.getValue().size();
        if (support == total) {
          continue; // Narrowing to a value every result has changes nothing.
        }
        var change = Change.add(field, Operator.EQ, typed.get(entry.getKey()));
        String text = field + ": " + entry.getKey();
        candidates.add(
            new Candidate(
                new Suggestion(
                    id(baseRequestId, text),
                    Suggestion.Kind.REFINE,
                    Suggestion.Source.RESULT_FACETS,
                    text,
                    List.of(change),
                    true,
                    support,
                    entry.getValue().stream().sorted().limit(5).toList(),
                    0),
                f,
                entry.getKey(),
                Math.abs(support - total / 2.0)));
      }
    }
    int order = facets.size();
    for (String text : rephrasings.stream().distinct().sorted().toList()) {
      candidates.add(
          new Candidate(
              new Suggestion(
                  id(baseRequestId, "rephrase:" + text),
                  Suggestion.Kind.REPHRASE,
                  Suggestion.Source.MODEL_REWRITE,
                  text,
                  List.of(),
                  false,
                  0,
                  List.of(),
                  0),
              order,
              text,
              Double.MAX_VALUE));
    }
    candidates.sort(
        Comparator.comparing((Candidate c) -> !c.suggestion().grounded())
            .thenComparingDouble(Candidate::balance)
            .thenComparingInt(Candidate::facetOrder)
            .thenComparing(Candidate::value));
    List<Suggestion> ranked = new ArrayList<>();
    for (Candidate c : candidates) {
      if (ranked.size() == limit) {
        break;
      }
      ranked.add(c.suggestion().at(ranked.size() + 1));
    }
    return new SuggestionSet(SuggestionSet.VERSION, baseRequestId, ranked);
  }

  static String display(FieldValue v) {
    return switch (v) {
      case FieldValue.Text t -> t.value();
      case FieldValue.Number n -> n.value().stripTrailingZeros().toPlainString();
      case FieldValue.Bool b -> Boolean.toString(b.value());
      case FieldValue.TextList l -> String.join(",", l.values());
    };
  }

  /** Stable id: the same base request and suggestion text always give the same id. */
  static String id(String base, String text) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(base.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      return HexFormat.of()
          .formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 24);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
