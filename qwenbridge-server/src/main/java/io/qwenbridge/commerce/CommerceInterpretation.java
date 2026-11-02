package io.qwenbridge.commerce;

import io.qwenbridge.catalog.canonical.CatalogVocabulary;
import io.qwenbridge.catalog.canonical.CommerceVocabulary;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.intent.IntentAnalyzer;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.queryir.SubQuery;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.validation.ConstraintValidator;
import io.qwenbridge.core.validation.DomainMapping;
import io.qwenbridge.core.validation.ValidationReport;
import io.qwenbridge.schema.DomainMappings;
import io.qwenbridge.schema.DomainPackSchemas;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Commerce interpretation layer (QB-086): it expresses a Commerce query as the universal,
 * domain-neutral QueryIR and maps that onto the Commerce schema. No Commerce field enters the
 * QueryIR contract itself; Commerce meaning comes from the pack's schema, mapping rules and
 * vocabulary.
 *
 * <ul>
 *   <li><b>Understanding.</b> Intents, retrieval needs and the decomposition come from the Core
 *       intent analyzer; subjective preferences (QB-088) from Core extraction's semantic signals.
 *   <li><b>Constraints.</b> The model's constraints, whose evidence the Commerce flow has already
 *       verified against the text, become HARD constraints with their spans. Core extraction's own
 *       constraint proposals are not added, so a query never has two competing sources of filters.
 *   <li><b>Canonical values.</b> Values are mapped to the catalog's canonical terms with the same
 *       vocabulary that normalizes documents (ADR 0029): {@code nike} becomes {@code Nike}, {@code
 *       noir} becomes {@code black}. Exact-match filters therefore compare like with like.
 *   <li><b>Validation.</b> The Core constraint validator checks each scope against the schema with
 *       the pack's approved mapping rules; a blocking result is reported, never weakened.
 * </ul>
 */
public final class CommerceInterpretation {
  static final DomainSchema SCHEMA =
      DomainPackSchemas.toSchema(new io.qwenbridge.packs.CommercePack().definition());
  static final DomainMapping MAPPING = new DomainMappings().forSchema("commerce");
  static final CatalogVocabulary VOCABULARY = CommerceVocabulary.builtIn();
  static final IntentAnalyzer INTENTS = new IntentAnalyzer();

  private CommerceInterpretation() {}

  /**
   * One executable part of the query. Without decomposition there is one scope covering the whole
   * query ({@code id} empty).
   *
   * @param commerceConstraints the Commerce constraints that apply to this scope
   * @param ir the universal QueryIR restricted to this scope
   */
  public record Scope(
      Optional<String> id,
      String objective,
      List<io.qwenbridge.analysis.model.Grounded.Constraint> commerceConstraints,
      QueryIR ir) {
    public Scope {
      commerceConstraints = List.copyOf(commerceConstraints);
    }
  }

  /** The universal QueryIR of a Commerce query. Constraint {@code i} has id {@code m<i+1>}. */
  public static QueryIR interpret(
      String original,
      Optional<LanguageTag> language,
      List<io.qwenbridge.analysis.model.Grounded.Constraint> model) {
    return interpret(original, language, model, "en");
  }

  /** The universal QueryIR, with the typed Commerce payload under {@code commerce} (QB-150). */
  public static QueryIR interpret(
      String original,
      Optional<LanguageTag> language,
      List<io.qwenbridge.analysis.model.Grounded.Constraint> model,
      String catalogLanguage) {
    var understanding =
        INTENTS.analyze(original, language, Set.of("commerce"), List.of("commerce"));
    var extraction =
        ConstraintExtractor.extract(
            original,
            new ExtractionContext(
                language,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(SCHEMA),
                EntityDictionary.EMPTY));
    List<Constraint> constraints = new ArrayList<>();
    for (int i = 0; i < model.size(); i++)
      constraints.add(universal("m" + (i + 1), original, model.get(i)));
    QueryIR ir =
        understanding.applyTo(QueryIR.of(original, language.orElse(LanguageTag.UNDETERMINED)));
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        List.of(),
        constraints,
        extraction.semanticSignals(),
        ir.retrievalHints(),
        ir.subqueries(),
        Map.of(
            CommerceQueryExtension.NAMESPACE,
            new CommerceQueryExtension(
                    "commerce@1",
                    catalogLanguage,
                    model.stream()
                        .filter(c -> c.field().equals("currency"))
                        .map(c -> c.value().strip().toUpperCase(Locale.ROOT))
                        .toList())
                .toExtension()));
  }

  static Constraint universal(
      String id, String original, io.qwenbridge.analysis.model.Grounded.Constraint c) {
    FieldDefinition field = SCHEMA.fields().get(c.field());
    String canonical = canonicalValue(c.field(), c.value());
    IrValue value =
        field == null
            ? new IrValue.Text(canonical)
            : switch (field.type()) {
              case NUMBER -> new IrValue.Number(new BigDecimal(canonical));
              case BOOLEAN -> new IrValue.Bool(Boolean.parseBoolean(canonical));
              case TEMPORAL ->
                  new IrValue.Temporal(IrValue.Temporal.Kind.DATE, canonical, Optional.empty());
              default -> new IrValue.Text(canonical);
            };
    var evidence = c.evidence();
    boolean grounded =
        "original".equals(evidence.source())
            && evidence.end() <= original.length()
            && original.substring(evidence.start(), evidence.end()).equals(evidence.quote());
    return new Constraint(
        id,
        c.field(),
        Constraint.Operator.valueOf(c.operator().name()),
        Optional.of(value),
        Constraint.Strength.HARD,
        grounded ? Constraint.Provenance.USER_TEXT : Constraint.Provenance.CONTEXT,
        grounded
            ? "stated in the query; model extraction with verified evidence"
            : "retained from the refined search",
        grounded
            ? Optional.of(Span.of(original, evidence.start(), evidence.end()))
            : Optional.empty(),
        Confidence.modelDecision(),
        Optional.empty());
  }

  /** The catalog's canonical form of a query value (shared vocabulary with documents). */
  static String canonicalValue(String field, String value) {
    String clean = value.strip();
    return switch (field) {
      case "brand" -> VOCABULARY.brand(clean).orElse(clean);
      case "category" -> VOCABULARY.category(clean).orElse(CatalogVocabulary.key(clean));
      case "currency" -> clean.toUpperCase(Locale.ROOT);
      default -> {
        var known = VOCABULARY.attributeValue(field, clean);
        if (known.isPresent()) yield known.get();
        yield VOCABULARY.lowercases(field) ? clean.toLowerCase(Locale.ROOT) : clean;
      }
    };
  }

  /**
   * QB-089: the executable scopes. With two or more subqueries each becomes a scope holding the
   * constraints whose evidence lies inside its part of the text, plus constraints stated outside
   * every part (they apply to all). Otherwise there is one scope for the whole query.
   */
  public static List<Scope> scopes(
      QueryIR ir, List<io.qwenbridge.analysis.model.Grounded.Constraint> model) {
    if (ir.subqueries().size() < 2)
      return List.of(new Scope(Optional.empty(), ir.originalText(), model, ir));
    String text = ir.originalText();
    List<int[]> segments = new ArrayList<>();
    int cursor = 0;
    for (SubQuery sq : ir.subqueries()) {
      int at = text.indexOf(sq.objective(), cursor);
      if (at < 0) return List.of(new Scope(Optional.empty(), text, model, ir));
      segments.add(new int[] {at, at + sq.objective().length()});
      cursor = at + sq.objective().length();
    }
    List<Scope> scopes = new ArrayList<>();
    for (int s = 0; s < ir.subqueries().size(); s++) {
      List<Constraint> own = new ArrayList<>();
      List<io.qwenbridge.analysis.model.Grounded.Constraint> commerce = new ArrayList<>();
      for (int i = 0; i < ir.constraints().size(); i++) {
        Constraint c = ir.constraints().get(i);
        int owner = owner(c, segments);
        if (owner == s || owner < 0) {
          own.add(c);
          commerce.add(model.get(i));
        }
      }
      SubQuery sq = ir.subqueries().get(s);
      int index = s;
      QueryIR scoped =
          new QueryIR(
              ir.version(),
              ir.queryId(),
              text,
              ir.language(),
              ir.intents(),
              ir.entities(),
              own,
              ir.semanticSignals().stream()
                  .filter(
                      g ->
                          g.span().isEmpty()
                              || g.span().get().start() >= segments.get(index)[0]
                                  && g.span().get().end() <= segments.get(index)[1])
                  .toList(),
              ir.retrievalHints(),
              List.of(),
              ir.extensions());
      scopes.add(new Scope(Optional.of(sq.id()), sq.objective(), commerce, scoped));
    }
    return scopes;
  }

  /** Index of the segment containing the constraint's span; -1 when it applies to all. */
  private static int owner(Constraint c, List<int[]> segments) {
    if (c.span().isEmpty()) return -1;
    for (int s = 0; s < segments.size(); s++)
      if (c.span().get().start() >= segments.get(s)[0]
          && c.span().get().end() <= segments.get(s)[1]) return s;
    return -1;
  }

  public static ValidationReport validate(QueryIR scoped) {
    return ConstraintValidator.validate(scoped, SCHEMA, MAPPING);
  }

  /** The QueryIR contract version, for the response. */
  public static String version(QueryIR ir) {
    return ir.version().toString();
  }
}
