package io.qwenbridge.core.translation;

import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.extraction.Extraction;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.extraction.Numerals;
import io.qwenbridge.core.extraction.Units;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Entity;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.PreservationIssue.Direction;
import io.qwenbridge.core.translation.PreservationIssue.Kind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that a translation kept the meaning that filters depend on (QB-018). It is domain-neutral:
 * it compares what the Core extractor and the shared number, unit and currency rules find in the
 * source and in the translation, as multisets per {@link Kind}.
 *
 * <ul>
 *   <li>Always checked, in any language: numbers that disappeared, unit symbols next to numbers
 *       (kg, cm, GB, %), dictionary entities and canonical entities, and exact identifiers, which
 *       must survive verbatim.
 *   <li>Checked only when both languages are in {@link ConstraintExtractor#CUE_LANGUAGES}:
 *       operators, negations, currencies, and numbers or entities the translator added (numbers may
 *       be written as words, and scripts without spaces defeat word-boundary matching). Otherwise
 *       these kinds are reported as unverified, never as preserved.
 * </ul>
 *
 * <p>The check is deliberately strict: a translation that changes a value, drops an exclusion or
 * invents a currency is rejected rather than repaired.
 */
public final class PreservationChecker {

  private static final Pattern NUMBER = Pattern.compile(Numerals.TOKEN);

  /** A unit symbol right after a number; Latin letters only so it works inside any script. */
  private static final Pattern UNIT_AFTER =
      Pattern.compile("\\G\\s?(%|°[cC]|[A-Za-zµ]{1,4})(?![A-Za-z])");

  private static final Pattern CURRENCY_AFTER =
      Pattern.compile("\\G\\s?(\\p{Sc}|[\\p{L}\\u200c]{1,12})(?![\\p{L}\\u200c])");

  private static final Pattern CURRENCY_BEFORE =
      Pattern.compile("(?<![\\p{L}\\u200c])(\\p{Sc}|[\\p{L}\\u200c]{1,12})\\s?$");

  private static final Pattern ASCII_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

  private static final Set<String> IDENTIFIER_TYPES =
      Set.of("identifier.code", "identifier.uuid", "network.ip_address");

  private PreservationChecker() {}

  public static PreservationReport check(
      String source,
      LanguageTag sourceLanguage,
      String translation,
      LanguageTag targetLanguage,
      EntityDictionary dictionary) {
    boolean covered = covered(sourceLanguage) && covered(targetLanguage);
    Facts before = facts(source, sourceLanguage, targetLanguage, dictionary);
    Facts after = facts(translation, targetLanguage, sourceLanguage, dictionary);

    List<PreservationIssue> issues = new ArrayList<>();
    Set<Kind> unverified = EnumSet.noneOf(Kind.class);
    for (Kind kind : Kind.values()) {
      boolean languageDependent =
          kind == Kind.OPERATOR || kind == Kind.NEGATION || kind == Kind.CURRENCY;
      if (languageDependent && !covered) {
        unverified.add(kind);
        continue;
      }
      Map<String, Integer> src = before.of(kind);
      Map<String, Integer> tgt = after.of(kind);
      difference(src, tgt)
          .ifPresent(s -> issues.add(new PreservationIssue(kind, Direction.LOST, s)));
      if ((kind == Kind.NUMBER || kind == Kind.ENTITY) && !covered) {
        // Additions cannot be judged: numbers may be written as words, and dictionary matching
        // needs word boundaries that scripts without spaces lack. Losses still are judged.
        unverified.add(kind);
        continue;
      }
      difference(tgt, src)
          .ifPresent(s -> issues.add(new PreservationIssue(kind, Direction.ADDED, s)));
    }
    return new PreservationReport(issues, unverified);
  }

  /** Whether operators, negations and currency words of this language are recognized. */
  public static boolean covered(LanguageTag language) {
    return ConstraintExtractor.CUE_LANGUAGES.contains(language.primaryLanguage());
  }

  private static Optional<List<String>> difference(
      Map<String, Integer> from, Map<String, Integer> other) {
    List<String> missing = new ArrayList<>();
    from.forEach(
        (key, count) -> {
          for (int i = other.getOrDefault(key, 0); i < count; i++) {
            missing.add(key);
          }
        });
    return missing.isEmpty() ? Optional.empty() : Optional.of(missing);
  }

  /**
   * @param other the language on the other side; a currency word that is ambiguous in this language
   *     (for example "kronor" kept in an English translation of Swedish text) is resolved with it
   */
  private static Facts facts(
      String text, LanguageTag language, LanguageTag other, EntityDictionary dictionary) {
    Facts facts = new Facts();
    Optional<LanguageTag> lang = determined(language);
    Optional<LanguageTag> otherLang = determined(other);
    boolean commaDecimal = Numerals.commaDecimal(lang);
    Matcher m = NUMBER.matcher(text);
    while (m.find()) {
      Numerals.parse(m.group(), commaDecimal)
          .ifPresent(p -> facts.add(Kind.NUMBER, p.value().stripTrailingZeros().toPlainString()));
      Matcher unit = UNIT_AFTER.matcher(text);
      if (unit.find(m.end())) {
        String symbol = unit.group(1);
        Units.unit(symbol)
            .filter(canonical -> canonical.equalsIgnoreCase(symbol))
            .ifPresent(canonical -> facts.add(Kind.UNIT, canonical));
      }
      currency(text, m.start(), m.end(), lang)
          .or(() -> currency(text, m.start(), m.end(), otherLang))
          .ifPresent(c -> facts.add(Kind.CURRENCY, c));
    }

    Extraction extraction =
        ConstraintExtractor.extract(
            text,
            new ExtractionContext(
                lang,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dictionary));
    for (Entity entity : extraction.entities()) {
      if (IDENTIFIER_TYPES.contains(entity.type())) {
        // Only ASCII codes must survive verbatim: the generic code pattern also matches words
        // with digits in other scripts ("サイズ42"), which a translation legitimately rewrites.
        if (!"identifier.code".equals(entity.type())
            || ASCII_CODE.matcher(entity.value()).matches()) {
          facts.add(Kind.IDENTIFIER, entity.value());
        }
      } else {
        entity.normalizedValue().ifPresent(v -> facts.add(Kind.ENTITY, entity.type() + ":" + v));
      }
    }
    for (Constraint c : extraction.constraints()) {
      if (c.negative()) {
        facts.add(Kind.NEGATION, c.operator().name());
      } else if (c.value().map(PreservationChecker::comparable).orElse(false)) {
        facts.add(Kind.OPERATOR, c.operator().name());
      }
    }
    return facts;
  }

  private static Optional<LanguageTag> determined(LanguageTag language) {
    return language.isUndetermined() || "mul".equals(language.primaryLanguage())
        ? Optional.empty()
        : Optional.of(language);
  }

  private static boolean comparable(IrValue value) {
    return value instanceof IrValue.Number
        || value instanceof IrValue.Quantity
        || value instanceof IrValue.Money
        || value instanceof IrValue.Range
        || value instanceof IrValue.Temporal;
  }

  private static Optional<String> currency(
      String text, int start, int end, Optional<LanguageTag> language) {
    Matcher after = CURRENCY_AFTER.matcher(text);
    if (after.find(end)) {
      Optional<String> code = Units.currency(after.group(1), language, Optional.empty());
      if (code.isPresent()) {
        return code;
      }
    }
    Matcher before = CURRENCY_BEFORE.matcher(text.substring(0, start));
    if (before.find()) {
      return Units.currency(before.group(1), language, Optional.empty());
    }
    return Optional.empty();
  }

  private static final class Facts {
    private final Map<Kind, Map<String, Integer>> byKind = new EnumMap<>(Kind.class);

    void add(Kind kind, String key) {
      byKind.computeIfAbsent(kind, k -> new TreeMap<>()).merge(key, 1, Integer::sum);
    }

    Map<String, Integer> of(Kind kind) {
      return byKind.getOrDefault(kind, Map.of());
    }
  }
}
