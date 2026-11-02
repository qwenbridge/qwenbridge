package io.qwenbridge.core.extraction;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.Constraint.Provenance;
import io.qwenbridge.core.queryir.Constraint.Strength;
import io.qwenbridge.core.queryir.Entity;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.Names;
import io.qwenbridge.core.queryir.SemanticSignal;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.text.Identifiers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, domain-neutral extraction of entities, constraints and semantic signals with
 * source spans (QB-029–QB-034). English, Swedish and Persian cue words; numbers in any digit
 * script.
 *
 * <p>Principles:
 *
 * <ul>
 *   <li><b>Grounded.</b> Every value has a span in the original text. A bare number without a
 *       comparison word or a field word is not a constraint.
 *   <li><b>Fields are justified.</b> A value is bound to a schema field mentioned just before it,
 *       else to the only schema field of a compatible type; otherwise the nearby words become a
 *       proposed field name that schema validation (Phase 8) will accept or reject, and the case is
 *       reported as {@link Unresolved.Kind#UNBOUND_FIELD}.
 *   <li><b>No guessing.</b> Ambiguous currencies stay empty, relative windows need a reference
 *       time, calendar days need a time zone; each gap is reported as {@link Unresolved}.
 *   <li><b>Hard unless softened.</b> Explicit comparisons and exclusions are HARD; preference
 *       wording ("preferably", "helst", "ترجیحاً") or approximation ("around") makes them SOFT. The
 *       reason is recorded. Subjective words are semantic signals, never filters.
 *   <li><b>Negation is kept.</b> An exclusion becomes a negative operator with its target; it is
 *       never turned into a positive preference.
 * </ul>
 */
public final class ConstraintExtractor {

  /**
   * Languages whose comparison, range and negation cues are in the lexicon (primary subtags).
   * Operators and exclusions in other languages are not recognized, so checks that depend on them
   * (for example translation preservation, QB-018) must report them as unverified.
   */
  public static final java.util.Set<String> CUE_LANGUAGES = java.util.Set.of("en", "sv", "fa");

  private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
  private static final String B = "(?<![\\p{L}\\p{M}\\u200c])";
  private static final String E = "(?![\\p{L}\\p{M}\\u200c])";
  private static final String NUM = "(" + Numerals.TOKEN + ")";
  private static final String UNIT = "(?:\\s?(%|°c|[\\p{L}\\u200c]{1,12}\\.?|\\p{Sc}))?";
  private static final String PRE = "(?:(\\p{Sc}|[A-Z]{3})\\s?)?";

  private record Cue(String phrase, Operator operator) {}

  private static final List<Cue> COMPARISONS =
      List.of(
          new Cue("no more than", Operator.LTE),
          new Cue("not more than", Operator.LTE),
          new Cue("up to", Operator.LTE),
          new Cue("at most", Operator.LTE),
          new Cue("maximum", Operator.LTE),
          new Cue("max", Operator.LTE),
          new Cue("högst", Operator.LTE),
          new Cue("maximalt", Operator.LTE),
          new Cue("حداکثر", Operator.LTE),
          new Cue("at least", Operator.GTE),
          new Cue("minimum", Operator.GTE),
          new Cue("min", Operator.GTE),
          new Cue("minst", Operator.GTE),
          new Cue("حداقل", Operator.GTE),
          new Cue("less than", Operator.LT),
          new Cue("cheaper than", Operator.LT),
          new Cue("under", Operator.LT),
          new Cue("below", Operator.LT),
          new Cue("mindre än", Operator.LT),
          new Cue("billigare än", Operator.LT),
          new Cue("کمتر از", Operator.LT),
          new Cue("زیر", Operator.LT),
          new Cue("ارزان‌تر از", Operator.LT),
          new Cue("more than", Operator.GT),
          new Cue("greater than", Operator.GT),
          new Cue("above", Operator.GT),
          new Cue("over", Operator.GT),
          new Cue("exceeding", Operator.GT),
          new Cue("mer än", Operator.GT),
          new Cue("över", Operator.GT),
          new Cue("بیشتر از", Operator.GT),
          new Cue("بالای", Operator.GT),
          new Cue("بالاتر از", Operator.GT),
          // Approximations are equality constraints that build() always marks SOFT.
          new Cue("approximately", Operator.EQ),
          new Cue("around", Operator.EQ),
          new Cue("about", Operator.EQ),
          new Cue("roughly", Operator.EQ),
          new Cue("ungefär", Operator.EQ),
          new Cue("cirka", Operator.EQ),
          new Cue("حدود", Operator.EQ),
          new Cue("تقریباً", Operator.EQ),
          new Cue("تقریبا", Operator.EQ));

  private static final Pattern BETWEEN =
      Pattern.compile(
          B
              + "(?:between|from|mellan|från|بین|از)\\s+"
              + PRE
              + NUM
              + UNIT
              + "\\s+(?:and|to|och|till|و|تا)\\s+"
              + PRE
              + NUM
              + UNIT
              + E,
          FLAGS);

  private static final Pattern NEGATION =
      Pattern.compile(
          B
              + "(not|no|without|excluding|except|inte|utan|ej|förutom|بدون|غیر|به جز|نه)\\s+"
              + "([\\p{L}\\p{M}\\u200c][\\p{L}\\p{M}\\u200c-]*(?:\\s+[\\p{L}\\p{M}\\u200c][\\p{L}\\p{M}\\u200c-]*){0,2})",
          FLAGS);

  private static final Set<String> NEGATION_STOP =
      Set.of(
          "and", "or", "but", "in", "with", "for", "och", "eller", "men", "i", "med", "för", "و",
          "یا", "در", "با", "برای");

  private static final Pattern PREFERENCE =
      Pattern.compile(
          B
              + "(preferably|ideally|if possible|would like|helst|gärna|om"
              + " möjligt|ترجیحا|ترجیحاً|اگر ممکن است)"
              + E,
          FLAGS);
  private static final Pattern APPROXIMATE =
      Pattern.compile(
          B + "(around|about|approximately|roughly|ungefär|cirka|ca|حدود|تقریبا|تقریباً)" + E,
          FLAGS);

  private static final Pattern RELATIVE_WINDOW =
      Pattern.compile(
          B
              + "(?:(?:in|during|over|within)\\s+)?(?:the\\s+)?(?:last|past|previous)\\s+(?:"
              + NUM
              + "\\s+)?"
              + "(minute|minutes|hour|hours|day|days|week|weeks)"
              + E
              + "|"
              + B
              + "(?:under\\s+)?(?:de\\s+)?senaste\\s+(?:"
              + NUM
              + "\\s+)?"
              + "(minuten|minuterna|timmen|timmarna|dagen|dagarna|veckan|veckorna)"
              + E
              + "|"
              + B
              + "(?:"
              + NUM
              + "\\s+)?(دقیقه|ساعت|روز|هفته)\\s+(?:ی\\s+)?گذشته"
              + E,
          FLAGS);

  private static final Pattern CALENDAR =
      Pattern.compile(B + "(today|yesterday|idag|igår|امروز|دیروز)" + E, FLAGS);

  private static final Pattern DATE_BOUND =
      Pattern.compile(
          B
              + "(after|since|before|until|efter|sedan|före|innan|بعد از|قبل"
              + " از)\\s+(\\d{4}-\\d{2}-\\d{2})"
              + E,
          FLAGS);

  private record Signal(String concept, String cue) {}

  private static final List<Signal> SIGNALS =
      List.of(
          new Signal("comfort", "comfortable"),
          new Signal("comfort", "comfy"),
          new Signal("comfort", "bekväm"),
          new Signal("comfort", "bekväma"),
          new Signal("comfort", "راحت"),
          new Signal("lightweight", "lightweight"),
          new Signal("lightweight", "lätt"),
          new Signal("lightweight", "lätta"),
          new Signal("lightweight", "سبک"),
          new Signal("elegance", "elegant"),
          new Signal("elegance", "stylish"),
          new Signal("elegance", "snygg"),
          new Signal("elegance", "شیک"),
          new Signal("suitability", "suitable for"),
          new Signal("suitability", "passar för"),
          new Signal("suitability", "مناسب برای"),
          new Signal("suitability", "برای"),
          new Signal("durability", "durable"),
          new Signal("durability", "hållbar"),
          new Signal("durability", "بادوام"),
          new Signal("suspiciousness", "suspicious"),
          new Signal("suspiciousness", "misstänkt"),
          new Signal("suspiciousness", "مشکوک"),
          new Signal("anomaly", "abnormal"),
          new Signal("anomaly", "unusual"),
          new Signal("anomaly", "anomalous"),
          new Signal("anomaly", "unusually high"),
          new Signal("anomaly", "ovanlig"),
          new Signal("anomaly", "غیرعادی"),
          new Signal("increase", "increased"),
          new Signal("increase", "increasing"),
          new Signal("increase", "spiking"),
          new Signal("increase", "ökad"),
          new Signal("increase", "افزایش"),
          new Signal("premium_quality", "premium"),
          new Signal("premium_quality", "high quality"),
          new Signal("premium_quality", "باکیفیت"));

  private ConstraintExtractor() {}

  public static Extraction extract(String text, ExtractionContext context) {
    String original = text == null ? "" : text;
    State s = new State(original, context);
    s.entities();
    s.ranges();
    s.comparisons();
    s.dates();
    s.windows();
    s.negations();
    s.signals();
    return new Extraction(s.entities, s.constraints, s.signals, s.unresolved);
  }

  private static final class State {
    final String text;
    final ExtractionContext context;
    final List<Entity> entities = new ArrayList<>();
    final List<Constraint> constraints = new ArrayList<>();
    final List<SemanticSignal> signals = new ArrayList<>();
    final List<Unresolved> unresolved = new ArrayList<>();
    final List<int[]> consumed = new ArrayList<>();

    State(String text, ExtractionContext context) {
      this.text = text;
      this.context = context;
    }

    // --- Entities (QB-029, QB-031) ---------------------------------------------------------

    void entities() {
      identifiers(Identifiers.IPV4, "network.ip_address");
      identifiers(Identifiers.UUID, "identifier.uuid");
      identifiers(Identifiers.CODE, "identifier.code");
      Matcher q = Identifiers.QUOTED.matcher(text);
      while (q.find()) {
        entity("phrase.quoted", q.start(), q.end(), Optional.empty(), Confidence.deterministic());
      }
      for (EntityDictionary.Term term : context.dictionary().terms()) {
        for (String alias : term.aliases()) {
          Matcher m = Pattern.compile(B + Pattern.quote(alias) + E, FLAGS).matcher(text);
          while (m.find()) {
            if (overlapsEntity(m.start(), m.end())) {
              continue;
            }
            entity(
                term.entityType(),
                m.start(),
                m.end(),
                Optional.of(term.canonical()),
                Confidence.deterministic());
          }
        }
      }
      countries();
    }

    private void identifiers(Pattern pattern, String type) {
      Matcher m = pattern.matcher(text);
      while (m.find()) {
        if (!overlapsEntity(m.start(), m.end())) {
          entity(type, m.start(), m.end(), Optional.empty(), Confidence.deterministic());
        }
      }
    }

    private void countries() {
      Matcher m = Gazetteer.PATTERN.matcher(text);
      while (m.find()) {
        if (!overlapsEntity(m.start(), m.end())) {
          entity(
              "location.country",
              m.start(),
              m.end(),
              Gazetteer.code(m.group()),
              Confidence.heuristic());
        }
      }
    }

    private boolean overlapsEntity(int start, int end) {
      return entities.stream().anyMatch(en -> start < en.span().end() && end > en.span().start());
    }

    private void entity(
        String type, int start, int end, Optional<String> normalized, Confidence confidence) {
      entities.add(
          new Entity(
              "e" + (entities.size() + 1),
              type,
              text.substring(start, end),
              normalized,
              Span.of(text, start, end),
              confidence));
    }

    // --- Numeric constraints (QB-030, QB-032) -------------------------------------------

    void ranges() {
      Matcher m = BETWEEN.matcher(text);
      while (m.find()) {
        Optional<IrValue> min = value(m.group(1), m.group(2), m.group(3), m.start(2));
        Optional<IrValue> max = value(m.group(4), m.group(5), m.group(6), m.start(5));
        if (min.isEmpty() || max.isEmpty()) {
          continue;
        }
        IrValue lo = min.get();
        IrValue hi = max.get();
        // "between 100 and 200 SEK": the trailing unit/currency applies to both bounds.
        if (lo instanceof IrValue.Number n && !(hi instanceof IrValue.Number)) {
          lo = sameKind(hi, n.value());
        }
        IrValue range = new IrValue.Range(Optional.of(lo), Optional.of(hi), true, true);
        int end = recognized(m.group(6)) ? m.end() : m.end(5);
        add(m.start(), end, Operator.BETWEEN, range, "explicit range");
      }
    }

    void comparisons() {
      for (Cue cue : COMPARISONS) {
        Pattern p =
            Pattern.compile(
                B
                    + Pattern.quote(cue.phrase()).replace(" ", "\\E\\s+\\Q")
                    + "\\s+"
                    + PRE
                    + NUM
                    + UNIT
                    + E,
                FLAGS);
        Matcher m = p.matcher(text);
        while (m.find()) {
          if (isConsumed(m.start(), m.end())) {
            continue;
          }
          int end = recognized(m.group(3)) ? m.end() : m.end(2);
          value(m.group(1), m.group(2), m.group(3), m.start(2))
              .ifPresent(
                  v ->
                      add(
                          m.start(),
                          end,
                          cue.operator(),
                          v,
                          "explicit comparison '" + cue.phrase() + "'"));
        }
      }
    }

    /** Builds a typed value from an optional prefix currency, the number and an optional suffix. */
    private Optional<IrValue> value(String prefix, String number, String suffix, int numberStart) {
      Optional<Numerals.Parsed> parsed = Numerals.parse(number, context.commaDecimal());
      if (parsed.isEmpty()) {
        return Optional.empty();
      }
      if (parsed.get().ambiguous()) {
        unresolved.add(
            new Unresolved(
                Unresolved.Kind.AMBIGUOUS_NUMBER,
                Span.of(text, numberStart, numberStart + number.length()),
                "separator could mean thousands or decimals; read with the request language's"
                    + " convention"));
      }
      BigDecimal amount = parsed.get().value();
      String currencySurface =
          prefix != null && Units.isCurrency(prefix)
              ? prefix
              : suffix != null && Units.isCurrency(stripDot(suffix)) ? stripDot(suffix) : null;
      if (currencySurface != null) {
        Optional<String> code =
            Units.currency(currencySurface, context.language(), context.locale());
        if (code.isEmpty()) {
          int at = text.indexOf(currencySurface, Math.max(0, numberStart - 8));
          int start = at < 0 ? numberStart : at;
          unresolved.add(
              new Unresolved(
                  Unresolved.Kind.AMBIGUOUS_CURRENCY,
                  Span.of(
                      text, start, start + (at < 0 ? number.length() : currencySurface.length())),
                  "names several currencies; no currency is assumed"));
        }
        return Optional.of(new IrValue.Money(amount, code));
      }
      if (suffix != null) {
        Optional<String> unit = Units.unit(stripDot(suffix));
        if (unit.isPresent()) {
          return Optional.of(new IrValue.Quantity(amount, unit.get()));
        }
      }
      return Optional.of(new IrValue.Number(amount));
    }

    private static boolean recognized(String suffix) {
      if (suffix == null) {
        return false;
      }
      String surface = stripDot(suffix);
      return Units.isCurrency(surface) || Units.unit(surface).isPresent();
    }

    private static String stripDot(String s) {
      return s.endsWith(".") && s.length() > 1 && !"kr.".equalsIgnoreCase(s)
          ? s.substring(0, s.length() - 1)
          : s;
    }

    private static IrValue sameKind(IrValue like, BigDecimal amount) {
      return switch (like) {
        case IrValue.Money m -> new IrValue.Money(amount, m.currency());
        case IrValue.Quantity q -> new IrValue.Quantity(amount, q.unit());
        default -> new IrValue.Number(amount);
      };
    }

    // --- Temporal constraints (QB-031) ----------------------------------------------------

    void windows() {
      Matcher m = RELATIVE_WINDOW.matcher(text);
      while (m.find()) {
        if (isConsumed(m.start(), m.end())) {
          continue;
        }
        String count = firstNonNull(m.group(1), m.group(3), m.group(5));
        String unit = firstNonNull(m.group(2), m.group(4), m.group(6)).toLowerCase(Locale.ROOT);
        long n =
            count == null
                ? 1
                : Numerals.parse(count, false).map(p -> p.value().longValue()).orElse(1L);
        Duration duration =
            switch (unit) {
              case "minute", "minutes", "minuten", "minuterna", "دقیقه" -> Duration.ofMinutes(n);
              case "hour", "hours", "timmen", "timmarna", "ساعت" -> Duration.ofHours(n);
              case "day", "days", "dagen", "dagarna", "روز" -> Duration.ofDays(n);
              default -> Duration.ofDays(7 * n);
            };
        Optional<String> reference = context.referenceTime().map(Object::toString);
        if (reference.isEmpty()) {
          unresolved.add(
              new Unresolved(
                  Unresolved.Kind.MISSING_REFERENCE_TIME,
                  Span.of(text, m.start(), m.end()),
                  "relative window kept unresolved"));
        }
        add(
            m.start(),
            m.end(),
            Operator.GTE,
            new IrValue.Temporal(
                IrValue.Temporal.Kind.RELATIVE_PAST, duration.toString(), reference),
            reference.isPresent()
                ? "explicit time window resolved against the reference time"
                : "explicit time window; no reference time");
      }
      Matcher c = CALENDAR.matcher(text);
      while (c.find()) {
        if (context.referenceTime().isEmpty() || context.zone().isEmpty()) {
          unresolved.add(
              new Unresolved(
                  Unresolved.Kind.MISSING_TIME_ZONE,
                  Span.of(text, c.start(), c.end()),
                  "calendar day needs a reference time and time zone"));
          continue;
        }
        LocalDate today = LocalDate.ofInstant(context.referenceTime().get(), context.zone().get());
        String word = c.group(1).toLowerCase(Locale.ROOT);
        LocalDate day =
            Set.of("yesterday", "igår", "دیروز").contains(word) ? today.minusDays(1) : today;
        add(
            c.start(),
            c.end(),
            Operator.EQ,
            new IrValue.Temporal(IrValue.Temporal.Kind.DATE, day.toString(), Optional.empty()),
            "calendar day resolved in time zone " + context.zone().get());
      }
    }

    void dates() {
      Matcher m = DATE_BOUND.matcher(text);
      while (m.find()) {
        String cue = m.group(1).toLowerCase(Locale.ROOT);
        Operator op =
            Set.of("before", "until", "före", "innan", "قبل از").contains(cue)
                ? Operator.LT
                : Operator.GTE;
        try {
          LocalDate.parse(m.group(2));
        } catch (java.time.DateTimeException e) {
          continue;
        }
        add(
            m.start(),
            m.end(),
            op,
            new IrValue.Temporal(IrValue.Temporal.Kind.DATE, m.group(2), Optional.empty()),
            "explicit date bound '" + cue + "'");
      }
    }

    // --- Negation (QB-033) ----------------------------------------------------------------

    void negations() {
      Matcher m = NEGATION.matcher(text);
      while (m.find()) {
        // Only the cue itself decides whether this negation was already used. The target window
        // is greedy and may run into a following comparison ("without leather under 1200 SEK");
        // it is cut where that comparison starts instead of losing the exclusion.
        if (isConsumed(m.start(), m.start(2))) {
          continue;
        }
        int targetStart = m.start(2);
        String window = text.substring(targetStart, firstConsumedAfter(targetStart, m.end()));
        String targetText = trimTarget(window.strip());
        if (targetText.isEmpty() || startsWithDigitOrComparison(targetText)) {
          continue;
        }
        int targetEnd = targetStart + targetText.length();
        Optional<Constraint> bound =
            negativeConstraint(m.start(), targetEnd, targetText, m.group(1));
        bound.ifPresent(
            c -> {
              constraints.add(c);
              consumed.add(new int[] {m.start(), targetEnd});
            });
      }
    }

    private Optional<Constraint> negativeConstraint(int start, int end, String target, String cue) {
      String reason = "explicit exclusion '" + cue + "'";
      String lowerTarget = target.toLowerCase(Locale.ROOT);
      // 1. A closed vocabulary value of a schema field.
      if (context.schema().isPresent()) {
        for (FieldDefinition field : context.schema().get().fields().values()) {
          for (String allowed : field.allowedValues()) {
            if (wordIn(lowerTarget, allowed.toLowerCase(Locale.ROOT))) {
              return Optional.of(
                  build(start, end, field.name(), Operator.NE, new IrValue.Text(allowed), reason));
            }
          }
        }
      }
      // 2. A dictionary term.
      for (EntityDictionary.Term term : context.dictionary().terms()) {
        for (String alias : term.aliases()) {
          if (wordIn(lowerTarget, alias.toLowerCase(Locale.ROOT))) {
            return Optional.of(
                build(
                    start,
                    end,
                    term.field(),
                    Operator.NE,
                    new IrValue.Text(term.canonical()),
                    reason));
          }
        }
      }
      // 3. A plain exclusion of the words themselves; never dropped, never made positive.
      return Optional.of(
          build(
              start,
              end,
              "content",
              Operator.NOT_CONTAINS,
              new IrValue.Text(target),
              reason + "; target bound to free text"));
    }

    private static boolean wordIn(String haystack, String word) {
      return Pattern.compile(B + Pattern.quote(word) + E, FLAGS).matcher(haystack).find();
    }

    /** Start of the first consumed range inside [from, to), or {@code to}. */
    private int firstConsumedAfter(int from, int to) {
      int cut = to;
      for (int[] r : consumed) {
        if (r[0] < cut && r[1] > from) {
          cut = Math.max(from, r[0]);
        }
      }
      return cut;
    }

    private static String trimTarget(String target) {
      String[] words = target.split("\\s+");
      StringBuilder out = new StringBuilder();
      for (String w : words) {
        if (NEGATION_STOP.contains(w.toLowerCase(Locale.ROOT))) {
          break;
        }
        if (!out.isEmpty()) {
          out.append(' ');
        }
        out.append(w);
      }
      return out.toString();
    }

    private static boolean startsWithDigitOrComparison(String target) {
      String lower = target.toLowerCase(Locale.ROOT);
      return lower.startsWith("more") || lower.startsWith("less") || lower.startsWith("later");
    }

    // --- Semantic signals (QB-034) --------------------------------------------------------

    void signals() {
      for (Signal signal : SIGNALS) {
        Matcher m =
            Pattern.compile(B + Pattern.quote(signal.cue()).replace(" ", "\\E\\s+\\Q") + E, FLAGS)
                .matcher(text);
        while (m.find()) {
          int start = m.start();
          int end = m.end();
          if (signals.stream()
              .anyMatch(
                  s -> s.span().filter(sp -> start < sp.end() && end > sp.start()).isPresent())) {
            continue;
          }
          boolean negated = precededBy(start, NEGATION_WORDS);
          signals.add(
              new SemanticSignal(
                  "g" + (signals.size() + 1),
                  signal.concept(),
                  negated ? SemanticSignal.Polarity.NEGATIVE : SemanticSignal.Polarity.POSITIVE,
                  Optional.empty(),
                  Optional.of(Span.of(text, start, end)),
                  Confidence.heuristic()));
        }
      }
    }

    private static final Pattern NEGATION_WORDS =
        Pattern.compile(B + "(not|no|inte|ej|نه|غیر)\\s+(?:too\\s+|för\\s+)?$", FLAGS);

    private boolean precededBy(int start, Pattern pattern) {
      return pattern.matcher(text.substring(Math.max(0, start - 16), start)).find();
    }

    // --- Building constraints ---------------------------------------------------------------

    private void add(int start, int end, Operator op, IrValue value, String reason) {
      constraints.add(build(start, end, bindField(start, end, value), op, value, reason));
      consumed.add(new int[] {start, end});
    }

    private Constraint build(
        int start, int end, String field, Operator op, IrValue value, String reason) {
      Strength strength = Strength.HARD;
      String why = reason;
      String before = text.substring(Math.max(0, start - 24), start);
      Matcher pref = PREFERENCE.matcher(before);
      Matcher approx =
          APPROXIMATE.matcher(
              text.substring(Math.max(0, start - 12), Math.min(text.length(), end)));
      if (pref.find()) {
        strength = Strength.SOFT;
        why = reason + "; softened by preference wording '" + pref.group(1) + "'";
      } else if (approx.find()) {
        strength = Strength.SOFT;
        why = reason + "; softened by approximation '" + approx.group(1) + "'";
      }
      return new Constraint(
          "c" + (constraints.size() + 1),
          field,
          op,
          Optional.of(value),
          strength,
          Provenance.USER_TEXT,
          why,
          Optional.of(Span.of(text, start, end)),
          Confidence.deterministic(),
          Optional.empty());
    }

    /**
     * Field named just before the value, else the unique compatible schema field, else proposed.
     */
    private String bindField(int start, int end, IrValue value) {
      List<String> words = wordsBefore(start, 3);
      Optional<DomainSchema> schema = context.schema();
      if (schema.isPresent()) {
        for (int n = Math.min(3, words.size()); n >= 1; n--) {
          String phrase = String.join("_", words.subList(words.size() - n, words.size()));
          for (FieldDefinition field : schema.get().fields().values()) {
            if (sameName(field.name(), phrase)) {
              return field.name();
            }
          }
        }
        List<FieldDefinition> compatible =
            schema.get().fields().values().stream().filter(f -> compatible(f, value)).toList();
        if (compatible.size() == 1) {
          return compatible.getFirst().name();
        }
      }
      String proposed = proposedName(words, value);
      unresolved.add(
          new Unresolved(
              Unresolved.Kind.UNBOUND_FIELD,
              Span.of(text, start, end),
              "field '" + proposed + "' is a proposal; it must be validated against a schema"));
      return proposed;
    }

    private static boolean compatible(FieldDefinition field, IrValue value) {
      if (value instanceof IrValue.Range r) {
        return r.min().or(r::max).map(v -> compatible(field, v)).orElse(false);
      }
      if (!field.type().accepts(value)) {
        return false;
      }
      return !(value instanceof IrValue.Quantity q)
          || field.unit().map(u -> u.equals(q.unit())).orElse(false);
    }

    private static boolean sameName(String fieldName, String phrase) {
      String normalizedField =
          fieldName
              .replace('.', '_')
              .replaceAll("([a-z])([A-Z])", "$1_$2")
              .toLowerCase(Locale.ROOT);
      String p = phrase.toLowerCase(Locale.ROOT);
      return normalizedField.equals(p)
          || normalizedField.equals(singular(p))
          || normalizedField.endsWith("_" + p)
          || normalizedField.endsWith("_" + singular(p));
    }

    private static String singular(String word) {
      return word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1) : word;
    }

    private List<String> wordsBefore(int start, int max) {
      String before = text.substring(0, start);
      Matcher m = Pattern.compile("[\\p{L}][\\p{L}\\p{M}\\p{N}\\u200c]*").matcher(before);
      List<String> words = new ArrayList<>();
      while (m.find()) {
        words.add(m.group().toLowerCase(Locale.ROOT));
      }
      return words.subList(Math.max(0, words.size() - max), words.size());
    }

    /**
     * Proposed field name without a schema. Numbers and quantities take their name from the
     * preceding words ("bot score below 10" -> bot_score, "memory usage above 85%" ->
     * memory_usage); money and times get generic names so that a nearby noun ("shoes under 1200
     * SEK") is never mistaken for a field.
     */
    private static String proposedName(List<String> words, IrValue value) {
      return switch (value) {
        case IrValue.Money m -> "amount";
        case IrValue.Temporal t -> "time";
        case IrValue.Range r -> r.min().map(v -> proposedName(words, v)).orElse("value");
        default -> {
          List<String> meaningful = words.stream().filter(w -> !GENERIC_WORDS.contains(w)).toList();
          String joined =
              String.join(
                  "_", meaningful.subList(Math.max(0, meaningful.size() - 2), meaningful.size()));
          yield Names.valid(joined)
              ? joined
              : value instanceof IrValue.Quantity ? "quantity" : "value";
        }
      };
    }

    private static final Set<String> GENERIC_WORDS =
        Set.of(
            "find", "show", "list", "get", "with", "and", "or", "for", "the", "a", "an", "is",
            "are", "in", "of", "costs", "cost", "priced");

    private boolean isConsumed(int start, int end) {
      return consumed.stream().anyMatch(r -> start < r[1] && end > r[0]);
    }

    private static String firstNonNull(String... values) {
      for (String v : values) {
        if (v != null) {
          return v;
        }
      }
      return null;
    }
  }
}
