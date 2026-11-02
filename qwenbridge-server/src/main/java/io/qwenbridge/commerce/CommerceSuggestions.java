package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.connectors.NeutralFilterTranslator;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.suggestion.Change;
import io.qwenbridge.core.suggestion.Feedback;
import io.qwenbridge.core.suggestion.Refinement;
import io.qwenbridge.core.suggestion.Suggester;
import io.qwenbridge.core.suggestion.Suggestion;
import io.qwenbridge.core.suggestion.SuggestionSet;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Typed suggestions, refinement and negative feedback for Commerce searches (QB-132 – QB-137).
 *
 * <ul>
 *   <li>Suggestions are computed from a stored search's returned results, so every refinement is
 *       backed by results that satisfy it; model rephrasings are labelled ungrounded and ranked
 *       last. Ids are stable for the same search.
 *   <li>Applying a suggestion or feedback changes only what it names: every other constraint is
 *       kept as it was, the result is re-validated against the schema and executed through the
 *       central policy, and its new constraints carry the suggestion or feedback as evidence.
 * </ul>
 */
@ApplicationScoped
public class CommerceSuggestions {
  @Inject CommerceAnalytics analytics;
  @Inject CommerceEvidenceStore evidence;
  @Inject CommerceSearchService search;

  @ConfigProperty(
      name = "qwenbridge.commerce.suggestions.facets",
      defaultValue = "brand,color,size,category,condition")
  List<String> facets;

  /** A typed change as JSON: the value as text, a plain decimal or true/false. */
  public record ChangeView(String op, String field, String operator, String value) {}

  public record SuggestionView(
      String id,
      String kind,
      String source,
      String text,
      List<ChangeView> changes,
      boolean grounded,
      int support,
      List<String> evidence,
      int rank) {
    public SuggestionView {
      changes = List.copyOf(changes);
      evidence = List.copyOf(evidence);
    }
  }

  public record FeedbackRequest(String kind, String documentId, String field, Object value) {}

  public record UpdateView(
      List<ChangeView> constraints, List<ChangeView> preferences, List<String> suppressed) {
    public UpdateView {
      constraints = List.copyOf(constraints);
      preferences = List.copyOf(preferences);
      suppressed = List.copyOf(suppressed);
    }
  }

  public record FeedbackResult(UpdateView update, SearchResponse result) {}

  private SearchResponse owned(ApiIdentity.Principal caller, String requestId) {
    caller.require("search");
    var previous = analytics.previous(caller, requestId);
    if (previous.queryIR() != null && !previous.queryIR().tenant().equals(caller.tenant()))
      throw new jakarta.ws.rs.ForbiddenException();
    return previous;
  }

  public SuggestionSet suggestions(ApiIdentity.Principal caller, String requestId) {
    return suggestions(owned(caller, requestId));
  }

  SuggestionSet suggestions(SearchResponse previous) {
    var constraints =
        previous.queryIR() == null
            ? List.<io.qwenbridge.core.queryir.Constraint>of()
            : ZeroResultRecovery.universal(previous);
    var results =
        previous.results().hits().stream()
            .map(h -> new Suggester.Result(h.id(), NeutralFilterTranslator.fields(h.document())))
            .toList();
    return Suggester.suggest(
        previous.requestId(), constraints, results, facets, previous.suggestions(), 10);
  }

  public static List<SuggestionView> view(SuggestionSet set) {
    return set.items().stream()
        .map(
            s ->
                new SuggestionView(
                    s.id(),
                    s.kind().name(),
                    s.source().name(),
                    s.text(),
                    s.changes().stream().map(CommerceSuggestions::view).toList(),
                    s.grounded(),
                    s.support(),
                    s.evidence(),
                    s.rank()))
        .toList();
  }

  static ChangeView view(Change c) {
    return new ChangeView(
        c.op().name(),
        c.field(),
        c.operator().name(),
        c.value().map(CommerceSuggestions::text).orElse(null));
  }

  static String text(FieldValue v) {
    return switch (v) {
      case FieldValue.Text t -> t.value();
      case FieldValue.Number n -> n.value().stripTrailingZeros().toPlainString();
      case FieldValue.Bool b -> Boolean.toString(b.value());
      case FieldValue.TextList l -> String.join(",", l.values());
    };
  }

  /** QB-133: applies one grounded refinement; everything else in the query stays. */
  public SearchResponse apply(
      ApiIdentity.Principal caller, String requestId, String suggestionId, String backend) {
    var previous = owned(caller, requestId);
    if (!"OK".equals(previous.status()) || previous.queryIR() == null)
      throw new IllegalArgumentException("Only a completed search can be refined");
    Suggestion suggestion =
        suggestions(previous).items().stream()
            .filter(s -> s.id().equals(suggestionId))
            .findFirst()
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Suggestion unavailable"));
    if (suggestion.kind() != Suggestion.Kind.REFINE)
      throw new IllegalArgumentException("A rephrasing is a new search: send it as a query");
    String description = "Refine by " + suggestion.text() + "; keep every other condition.";
    return execute(caller, previous, suggestion.changes(), description, backend);
  }

  /** QB-136: negative feedback as a typed update, applied as a follow-up search. */
  public FeedbackResult feedback(
      ApiIdentity.Principal caller, String requestId, FeedbackRequest request, String backend) {
    var previous = owned(caller, requestId);
    if (!"OK".equals(previous.status()) || previous.queryIR() == null)
      throw new IllegalArgumentException("Feedback applies to a completed search");
    if (request == null || request.kind() == null)
      throw new IllegalArgumentException("Feedback kind required");
    Feedback.Kind kind;
    try {
      kind = Feedback.Kind.valueOf(request.kind());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown feedback kind", ex);
    }
    var signal =
        new Feedback.Signal(
            kind,
            Optional.ofNullable(request.documentId()),
            Optional.ofNullable(request.field()),
            Optional.ofNullable(request.value()).map(CommerceSuggestions::typed));
    Map<String, Map<String, FieldValue>> current = new LinkedHashMap<>();
    for (SearchHit hit : previous.results().hits())
      current.put(hit.id(), NeutralFilterTranslator.fields(hit.document()));
    var update = Feedback.interpret(signal, current);
    String description =
        switch (kind) {
          case NOT_RELEVANT -> "Feedback: result " + request.documentId() + " is not relevant.";
          case EXCLUDE_VALUE ->
              "Feedback: exclude " + request.field() + " " + text(signal.value().get()) + ".";
          case TOO_EXPENSIVE -> "Feedback: result " + request.documentId() + " is too expensive.";
        };
    var result = execute(caller, previous, update.hard(), description, backend);
    var suppressed = new HashSet<>(update.suppressed());
    List<SearchHit> hits =
        new ArrayList<>(
            result.results().hits().stream().filter(h -> !suppressed.contains(h.id())).toList());
    // A SOFT preference ranks, never filters: results that satisfy it move ahead, order kept.
    for (Change preference : update.preferences()) {
      var filter =
          new io.qwenbridge.core.retrieval.filter.Filter.Compare(
              preference.field(),
              io.qwenbridge.core.retrieval.filter.Filter.Compare.Op.LT,
              preference.value().orElseThrow());
      List<SearchHit> first = new ArrayList<>();
      List<SearchHit> rest = new ArrayList<>();
      for (SearchHit h : hits)
        (io.qwenbridge.core.retrieval.filter.FilterEvaluator.matches(
                    filter, NeutralFilterTranslator.fields(h.document()))
                ? first
                : rest)
            .add(h);
      hits = new ArrayList<>(first);
      hits.addAll(rest);
    }
    int removed = result.results().hits().size() - hits.size();
    var results =
        new SearchResultSet(
            hits,
            Math.max(0, result.results().totalHits() - removed),
            result.results().tookMillis());
    return new FeedbackResult(
        new UpdateView(
            update.hard().stream().map(CommerceSuggestions::view).toList(),
            update.preferences().stream().map(CommerceSuggestions::view).toList(),
            update.suppressed()),
        result.withResults(results));
  }

  static FieldValue typed(Object value) {
    return switch (value) {
      case Boolean b -> new FieldValue.Bool(b);
      case Number n -> new FieldValue.Number(new BigDecimal(n.toString()));
      case String s -> FieldValue.text(s);
      default ->
          throw new IllegalArgumentException("Feedback value must be text, number or boolean");
    };
  }

  /**
   * Applies typed changes to the stored plan through the Core refinement (untouched constraints are
   * the same objects), re-validates and executes.
   */
  private SearchResponse execute(
      ApiIdentity.Principal caller,
      SearchResponse previous,
      List<Change> changes,
      String description,
      String backend) {
    var commerce = previous.queryIR().constraints();
    var source =
        evidence.recordQuery(
            caller.tenant(),
            previous.requestId(),
            description,
            List.of(new Evidence("original", 0, description.length(), description)),
            previous.evidence().isEmpty() ? null : previous.evidence().getFirst().id());
    evidence.bindOwner(caller.tenant(), source.id(), caller.subject());
    var refined =
        Refinement.apply(
            ZeroResultRecovery.universal(previous),
            changes,
            (change, i) ->
                new io.qwenbridge.core.queryir.Constraint(
                    "r" + i,
                    change.field(),
                    change.operator(),
                    change.value().map(v -> (IrValue) new IrValue.Text(text(v))),
                    io.qwenbridge.core.queryir.Constraint.Strength.HARD,
                    io.qwenbridge.core.queryir.Constraint.Provenance.CONTEXT,
                    description,
                    Optional.empty(),
                    Confidence.deterministic(),
                    Optional.empty()));
    String parentEvidence =
        previous.evidence().isEmpty() ? null : previous.evidence().getFirst().id();
    List<Constraint> constraints = new ArrayList<>();
    for (var c : refined) {
      if (c.id().startsWith("m")) {
        var kept = commerce.get(Integer.parseInt(c.id().substring(1)) - 1);
        constraints.add(
            kept.evidence().source().equals("original") && parentEvidence != null
                ? new Constraint(
                    kept.field(),
                    kept.operator(),
                    kept.value(),
                    new Evidence(
                        "evidence:" + parentEvidence,
                        kept.evidence().start(),
                        kept.evidence().end(),
                        kept.evidence().quote()))
                : kept);
      } else {
        constraints.add(
            new Constraint(
                c.field(),
                Operator.valueOf(c.operator().name()),
                ((IrValue.Text) c.value().orElseThrow()).value(),
                new Evidence("evidence:" + source.id(), 0, description.length(), description)));
      }
    }
    var prior = previous.queryIR();
    var plan =
        CommerceCompiler.validate(
            new Schema(prior.schemaId(), prior.schemaVersion(), prior.fields()),
            caller.tenant(),
            constraints);
    return search.executeApproved(caller, previous, plan, description, backend);
  }
}
