package io.qwenbridge.core.explain;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.evidence.EvidenceRecord;
import io.qwenbridge.core.evidence.EvidenceSet;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Entity;
import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.SemanticSignal;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.schema.SchemaViolation;
import io.qwenbridge.core.security.Redactor;
import io.qwenbridge.core.validation.ConstraintAssessment;
import io.qwenbridge.core.validation.ValidationReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Explains a QueryIR from what was recorded while building and validating it (QB-081, QB-082): the
 * span each element came from, its provenance, the recorded reason and strength, and the validation
 * gate's verdict. Statements are filled-in templates; nothing is generated. Elements without
 * grounding evidence get INSUFFICIENT_EVIDENCE instead of a plausible-sounding story. Quoted user
 * text passes through {@link Redactor} (QB-084).
 */
public final class QueryIrExplainer {

  private QueryIrExplainer() {}

  public static ExplanationSet explain(
      QueryIR ir, Optional<ValidationReport> validation, Instant observedAt) {
    List<Explanation> explanations = new ArrayList<>();
    List<EvidenceRecord> evidence = new ArrayList<>();
    Map<String, ConstraintAssessment> assessments =
        validation
            .map(
                v ->
                    v.assessments().stream()
                        .collect(
                            Collectors.toMap(
                                ConstraintAssessment::constraintId, a -> a, (a, b) -> a)))
            .orElse(Map.of());

    for (Intent intent : ir.intents()) {
      String subject = "intent:" + intent.id();
      if (intent.span().isPresent()) {
        String ev = "ev-" + intent.id();
        evidence.add(EvidenceRecord.userText(ev, intent.span().get(), observedAt));
        explanations.add(
            Explanation.supported(
                subject,
                "Intent " + intent.type() + " recognized from " + quote(intent.span().get()) + ".",
                List.of(ev),
                intent.confidence()));
      } else if (intent.confidence().kind() == Confidence.Kind.UNKNOWN) {
        explanations.add(Explanation.unknown(subject));
      } else {
        explanations.add(Explanation.insufficient(subject, intent.confidence()));
      }
    }

    for (Entity entity : ir.entities()) {
      String ev = "ev-" + entity.id();
      evidence.add(EvidenceRecord.userText(ev, entity.span(), observedAt));
      String value = entity.normalizedValue().map(v -> " (" + safe(v) + ")").orElse("");
      explanations.add(
          Explanation.supported(
              "entity:" + entity.id(),
              "Entity " + entity.type() + value + " found in " + quote(entity.span()) + ".",
              List.of(ev),
              entity.confidence()));
    }

    for (Constraint c : ir.constraints()) {
      explanations.add(constraint(c, assessments.get(c.id()), evidence, observedAt));
    }

    for (SemanticSignal s : ir.semanticSignals()) {
      String subject = "signal:" + s.id();
      if (s.span().isEmpty()) {
        explanations.add(Explanation.insufficient(subject, s.confidence()));
        continue;
      }
      String ev = "ev-" + s.id();
      evidence.add(EvidenceRecord.userText(ev, s.span().get(), observedAt));
      explanations.add(
          Explanation.supported(
              subject,
              "Preference "
                  + s.concept()
                  + " ("
                  + s.polarity().name().toLowerCase(Locale.ROOT)
                  + ") from "
                  + quote(s.span().get())
                  + "; used for ranking, never as a filter.",
              List.of(ev),
              s.confidence()));
    }
    // A quoted fragment may hold only a secret's value (e.g. the token after "token="), which is
    // not recognizable on its own; mask every value that is a secret in the whole query (QB-084).
    java.util.Set<String> secrets = Redactor.secretValues(ir.originalText());
    List<Explanation> masked =
        explanations.stream()
            .map(
                e ->
                    e.statement().isEmpty()
                        ? e
                        : new Explanation(
                            e.subject(),
                            e.status(),
                            e.statement().map(st -> Redactor.redact(st, secrets)),
                            e.evidenceIds(),
                            e.confidence()))
            .toList();
    EvidenceSet set = new EvidenceSet(evidence);
    return ExplanationSet.checked(masked, set, ir.originalText());
  }

  private static Explanation constraint(
      Constraint c, ConstraintAssessment assessment, List<EvidenceRecord> evidence, Instant at) {
    String subject = "constraint:" + c.id();
    List<String> cited = new ArrayList<>();
    String origin;
    if (c.span().isPresent()) {
      String ev = "ev-" + c.id();
      evidence.add(EvidenceRecord.userText(ev, c.span().get(), at));
      cited.add(ev);
      origin =
          c.provenance() == Constraint.Provenance.INFERRED
              ? "inferred from " + quote(c.span().get())
              : "from " + quote(c.span().get());
    } else if (c.provenance() == Constraint.Provenance.CONTEXT) {
      String ev = "ev-" + c.id() + "-context";
      evidence.add(
          EvidenceRecord.of(ev, EvidenceRecord.Kind.CONTEXT, "context", "conversation", null, at));
      cited.add(ev);
      origin = "carried from validated context";
    } else if (c.provenance() == Constraint.Provenance.DOMAIN_RULE) {
      String ev = "ev-" + c.id() + "-rule";
      evidence.add(
          EvidenceRecord.of(ev, EvidenceRecord.Kind.DOMAIN_RULE, "rule", "domain", null, at));
      cited.add(ev);
      origin = "added by a domain rule";
    } else {
      return Explanation.insufficient(subject, c.confidence());
    }
    StringBuilder statement =
        new StringBuilder()
            .append(c.strength() == Constraint.Strength.HARD ? "Requirement " : "Preference ")
            .append(c.field())
            .append(' ')
            .append(operator(c.operator()))
            .append(c.value().map(v -> " " + render(v)).orElse(""))
            .append(", ")
            .append(origin)
            .append(" (")
            .append(safe(c.reason()))
            .append(").");
    if (assessment != null) {
      String ev = "ev-" + c.id() + "-validation";
      evidence.add(
          new EvidenceRecord(
              EvidenceRecord.VERSION,
              ev,
              EvidenceRecord.Kind.POLICY_DECISION,
              "validation",
              assessment.status().name(),
              Optional.empty(),
              Optional.empty(),
              at,
              Optional.of(cited.getFirst()),
              Map.of()));
      cited.add(ev);
      statement.append(" Validation: ").append(assessment.status().name());
      if (!assessment.violations().isEmpty()) {
        statement
            .append(" (")
            .append(
                assessment.violations().stream()
                    .map(SchemaViolation::code)
                    .map(Enum::name)
                    .distinct()
                    .collect(Collectors.joining(", ")))
            .append(')');
      }
      statement.append('.');
    }
    return Explanation.supported(subject, statement.toString(), cited, c.confidence());
  }

  private static String quote(Span span) {
    return "\"" + safe(span.text()) + "\"";
  }

  private static String safe(String text) {
    return Redactor.redact(text);
  }

  private static String operator(Constraint.Operator op) {
    return switch (op) {
      case EQ -> "=";
      case NE -> "≠";
      case LT -> "<";
      case LTE -> "≤";
      case GT -> ">";
      case GTE -> "≥";
      case BETWEEN -> "between";
      case IN -> "in";
      case NOT_IN -> "not in";
      case CONTAINS -> "contains";
      case NOT_CONTAINS -> "does not contain";
      case EXISTS -> "exists";
      case NOT_EXISTS -> "does not exist";
    };
  }

  static String render(IrValue value) {
    return switch (value) {
      case IrValue.Text t -> safe(t.value());
      case IrValue.Number n -> plain(n.value());
      case IrValue.Quantity q -> plain(q.value()) + " " + q.unit();
      case IrValue.Money m ->
          plain(m.amount()) + m.currency().map(c -> " " + c).orElse(" (currency unknown)");
      case IrValue.Bool b -> Boolean.toString(b.value());
      case IrValue.Temporal t -> t.value();
      case IrValue.Range r ->
          r.min().map(QueryIrExplainer::render).orElse("…")
              + " – "
              + r.max().map(QueryIrExplainer::render).orElse("…");
      case IrValue.ListOf l ->
          l.values().stream()
              .map(QueryIrExplainer::render)
              .collect(Collectors.joining(", ", "[", "]"));
    };
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
