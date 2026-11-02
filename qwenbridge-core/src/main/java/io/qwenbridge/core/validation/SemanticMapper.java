package io.qwenbridge.core.validation;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.Constraint.Provenance;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.SemanticSignal;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applies approved {@link MappingRule}s and type-based rebinding to a QueryIR for one schema
 * (QB-052, QB-053). It never invents fields: a field that is neither in the schema, nor reachable
 * through an approved rule, nor the single compatible field of its value type stays as it is and is
 * rejected by validation.
 *
 * <p>Order: aliases, then money splitting, then rebinding of extraction proposals by value type,
 * then approved signal interpretations (always SOFT and INFERRED). Spans, strength, provenance and
 * negation of user constraints are preserved; every change is appended to the reason.
 */
public final class SemanticMapper {

  /**
   * Placeholder names extraction proposes when the text names no field ("under 1200 SEK" ->
   * amount). Only these may be bound by type; a field the user named ("warranty 3") is never
   * re-targeted to another field just because the value type fits.
   */
  static final Set<String> GENERIC_PROPOSALS = Set.of("value", "quantity", "amount", "time");

  private SemanticMapper() {}

  public static QueryIR map(QueryIR ir, DomainSchema schema, DomainMapping mapping) {
    List<Constraint> mapped = new ArrayList<>();
    int next = ir.constraints().size() + 1;
    for (Constraint c : ir.constraints()) {
      Constraint current = alias(c, mapping);
      Optional<MappingRule.MoneyToNumberAndCurrency> split = moneySplit(current, schema, mapping);
      if (split.isPresent()) {
        IrValue.Money money = (IrValue.Money) current.value().orElseThrow();
        MappingRule.MoneyToNumberAndCurrency rule = split.get();
        mapped.add(
            with(
                current,
                rule.numberField(),
                current.operator(),
                new IrValue.Number(money.amount()),
                "mapped by rule " + rule.id()));
        if (money.currency().isPresent()) {
          mapped.add(
              new Constraint(
                  "c" + next++,
                  rule.currencyField(),
                  Operator.EQ,
                  Optional.of(new IrValue.Text(money.currency().get())),
                  current.strength(),
                  current.provenance(),
                  "currency of " + current.id() + "; mapped by rule " + rule.id(),
                  current.span(),
                  current.confidence(),
                  current.scope()));
        }
        continue;
      }
      if (!schema.fields().containsKey(current.field())
          && GENERIC_PROPOSALS.contains(current.field())) {
        current = rebindByType(current, schema).orElse(current);
      }
      mapped.add(current);
    }
    for (SemanticSignal signal : ir.semanticSignals()) {
      for (MappingRule rule : mapping.rules()) {
        if (rule instanceof MappingRule.SignalToSoftConstraint s
            && s.concept().equals(signal.concept())
            && s.polarity() == signal.polarity()) {
          mapped.add(
              new Constraint(
                  "c" + next++,
                  s.field(),
                  s.operator(),
                  Optional.of(s.value()),
                  Strength.SOFT,
                  Provenance.INFERRED,
                  "interpretation of signal "
                      + signal.id()
                      + " ("
                      + signal.concept()
                      + ") by rule "
                      + s.id(),
                  signal.span(),
                  Confidence.heuristic(),
                  Optional.empty()));
        }
      }
    }
    return new QueryIR(
        ir.version(),
        ir.queryId(),
        ir.originalText(),
        ir.language(),
        ir.intents(),
        ir.entities(),
        mapped,
        ir.semanticSignals(),
        ir.retrievalHints(),
        ir.subqueries(),
        ir.extensions());
  }

  private static Constraint alias(Constraint c, DomainMapping mapping) {
    for (MappingRule rule : mapping.rules()) {
      if (rule instanceof MappingRule.FieldAlias a && a.from().equals(c.field())) {
        return with(
            c, a.to(), c.operator(), c.value().orElse(null), "field alias by rule " + a.id());
      }
    }
    return c;
  }

  private static Optional<MappingRule.MoneyToNumberAndCurrency> moneySplit(
      Constraint c, DomainSchema schema, DomainMapping mapping) {
    // Exclusions are never split: "not 100 SEK" is not "not 100 and SEK". They stay as they are and
    // are reported if the schema cannot express them.
    if (!(c.value().orElse(null) instanceof IrValue.Money) || c.negative()) {
      return Optional.empty();
    }
    FieldDefinition current = schema.fields().get(c.field());
    if (current == null || current.type().accepts(c.value().get())) {
      return Optional.empty(); // the schema has a real MONEY field here
    }
    for (MappingRule rule : mapping.rules()) {
      // Only the declared number field: an unknown field must reach it through an approved alias.
      if (rule instanceof MappingRule.MoneyToNumberAndCurrency m
          && c.field().equals(m.numberField())) {
        return Optional.of(m);
      }
    }
    return Optional.empty();
  }

  /** The single schema field whose type (and unit) fits the value, if exactly one exists. */
  private static Optional<Constraint> rebindByType(Constraint c, DomainSchema schema) {
    if (c.value().isEmpty()) {
      return Optional.empty();
    }
    IrValue probe =
        c.value().get() instanceof IrValue.Range r
            ? r.min().or(r::max).orElseThrow()
            : c.value().get();
    if (probe instanceof IrValue.ListOf l && !l.values().isEmpty()) {
      probe = l.values().getFirst();
    }
    IrValue sample = probe;
    List<FieldDefinition> compatible =
        schema.fields().values().stream()
            .filter(f -> f.type().accepts(sample))
            .filter(
                f ->
                    !(sample instanceof IrValue.Quantity q)
                        || f.unit().map(q.unit()::equals).orElse(false))
            .filter(f -> f.operators().contains(c.operator()))
            .toList();
    if (compatible.size() != 1) {
      return Optional.empty();
    }
    return Optional.of(
        with(
            c,
            compatible.getFirst().name(),
            c.operator(),
            c.value().get(),
            "field '" + c.field() + "' bound by type at validation"));
  }

  private static Constraint with(
      Constraint c, String field, Operator op, IrValue value, String note) {
    return new Constraint(
        c.id(),
        field,
        op,
        Optional.ofNullable(value),
        c.strength(),
        c.provenance(),
        c.reason() + "; " + note,
        c.span(),
        c.confidence(),
        c.scope());
  }
}
