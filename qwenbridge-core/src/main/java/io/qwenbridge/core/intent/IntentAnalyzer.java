package io.qwenbridge.core.intent;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.RetrievalHints.Mode;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.queryir.SubQuery;
import io.qwenbridge.core.request.LanguageTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, domain-neutral intent analysis (QB-022–QB-027). It runs without any model, so a
 * provider outage never removes intent understanding; model-assisted classifiers may refine its
 * output but cannot replace the explicit statuses it produces.
 *
 * <ul>
 *   <li><b>Intents</b> (QB-023): every cue occurrence becomes a typed intent with its span and
 *       {@code HEURISTIC} confidence; several intents may be present. A request with real content
 *       but no cue is an <em>implicit</em> search, recorded as such with the whole text as span —
 *       never a silent default.
 *   <li><b>Retrieval needs</b> (QB-024): overlapping {@link RetrievalHints} derived from the text
 *       (identifiers → EXACT, comparisons/measures/negation → STRUCTURED, subjective cues or
 *       descriptive text → SEMANTIC, content words → LEXICAL). No backend is chosen here.
 *   <li><b>Decomposition</b> (QB-025): sequential steps ("and then") depend on the previous step;
 *       parallel items ("and a ...") are independent; a shared tail (", both ...") applies to all
 *       items. The resulting graph is acyclic by construction.
 *   <li><b>Assessment</b> (QB-026, QB-027): vague-only requests need clarification; requests for a
 *       domain that is not enabled are out of domain. Each issue yields one focused question in the
 *       request's language when available (en, sv, fa), else English.
 * </ul>
 */
public final class IntentAnalyzer {

  private static final Pattern SEQUENTIAL =
      Pattern.compile(
          "(?i)(?:,?(?<!\\s)\\s+and\\s+then\\s+|,\\s*then\\s+|(?<!\\s)\\s+och\\s+sedan\\s+|(?<!\\s)\\s+و\\s+سپس\\s+|(?<!\\s)\\s+و\\s+بعد\\s+)");
  private static final Pattern PARALLEL =
      Pattern.compile(
          "(?i)(?:(?<!\\s)\\s+(?:and|och|plus)\\s+(?=(?:a|an|also|en|ett)\\s)|(?<!\\s)\\s+و\\s+(?=یک\\s))");
  private static final Pattern SHARED_TAIL =
      Pattern.compile("(?i),\\s*(?:both|båda|هر\\s+دو)\\s.*$");

  private final IntentTaxonomy taxonomy;

  public IntentAnalyzer(IntentTaxonomy taxonomy) {
    this.taxonomy = taxonomy;
  }

  public IntentAnalyzer() {
    this(IntentTaxonomy.core());
  }

  /**
   * @param text the original request text (spans refer to it)
   * @param language detected or declared language, used for question wording only
   * @param enabledDomains ids of domain schemas enabled in this deployment
   * @param domainHints domains the client asked for, possibly none
   */
  public IntentUnderstanding analyze(
      String text,
      Optional<LanguageTag> language,
      Set<String> enabledDomains,
      List<String> domainHints) {
    String safe = text == null ? "" : text;
    List<Lexicon.Match> cues = new ArrayList<>();
    List<Intent> intents = detectIntents(safe, cues);
    boolean hasSubject = hasSubject(safe, cues);
    if (intents.isEmpty() && hasSubject) {
      intents =
          List.of(new Intent("i1", IntentTaxonomy.SEARCH, Confidence.heuristic(), wholeSpan(safe)));
    }
    RetrievalHints hints = hasSubject ? retrievalNeeds(safe) : RetrievalHints.NONE;
    List<SubQuery> subqueries = decompose(safe, intents);
    QueryAssessment assessment =
        assess(
            hasSubject,
            language,
            enabledDomains == null ? Set.of() : enabledDomains,
            domainHints == null ? List.of() : domainHints);
    return new IntentUnderstanding(IntentTaxonomy.VERSION, intents, hints, subqueries, assessment);
  }

  // --- Intents ---------------------------------------------------------------------------

  private List<Intent> detectIntents(String text, List<Lexicon.Match> allCues) {
    record Hit(String type, Lexicon.Match match) {}
    List<Hit> hits = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : Lexicon.INTENT_CUES.entrySet()) {
      for (Lexicon.Match m : Lexicon.find(text, e.getValue())) {
        hits.add(new Hit(e.getKey(), m));
      }
    }
    // Longest match wins where cues overlap ("show me" over "show"); keep text order.
    hits.sort(
        Comparator.comparingInt((Hit h) -> h.match().start())
            .thenComparingInt(h -> -(h.match().end() - h.match().start())));
    List<Intent> intents = new ArrayList<>();
    int coveredUntil = -1;
    for (Hit hit : hits) {
      if (hit.match().start() < coveredUntil || !taxonomy.isKnown(hit.type())) {
        continue;
      }
      coveredUntil = hit.match().end();
      allCues.add(hit.match());
      intents.add(
          new Intent(
              "i" + (intents.size() + 1),
              hit.type(),
              Confidence.heuristic(),
              Optional.of(new Span(hit.match().start(), hit.match().end(), hit.match().text()))));
    }
    return intents;
  }

  private static boolean hasSubject(String text, List<Lexicon.Match> cues) {
    for (Lexicon.Match word : Lexicon.words(text)) {
      boolean insideCue =
          cues.stream().anyMatch(c -> word.start() >= c.start() && word.end() <= c.end());
      if (!insideCue
          && !Lexicon.STOPWORDS.contains(word.text())
          && !Lexicon.VAGUE.contains(word.text())) {
        return true;
      }
    }
    return false;
  }

  private static Optional<Span> wholeSpan(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return end > start ? Optional.of(Span.of(text, start, end)) : Optional.empty();
  }

  // --- Retrieval needs -----------------------------------------------------------------------

  static RetrievalHints retrievalNeeds(String text) {
    EnumSet<Mode> modes = EnumSet.of(Mode.LEXICAL);
    boolean exact = Lexicon.hasExactIdentifier(text);
    boolean structured =
        Lexicon.hasComparatorOrMeasure(text)
            || !Lexicon.find(text, Lexicon.STRUCTURED_CUES).isEmpty()
            || !Lexicon.find(text, Lexicon.NEGATION_CUES).isEmpty();
    boolean subjective = !Lexicon.find(text, Lexicon.SEMANTIC_CUES).isEmpty();
    long contentWords =
        Lexicon.words(text).stream()
            .filter(w -> !Lexicon.STOPWORDS.contains(w.text()) && !Lexicon.VAGUE.contains(w.text()))
            .count();
    if (exact) {
      modes.add(Mode.EXACT);
    }
    if (structured) {
      modes.add(Mode.STRUCTURED);
    }
    if (subjective || (!exact && !structured && contentWords >= 3)) {
      modes.add(Mode.SEMANTIC);
    }
    return new RetrievalHints(modes, false, List.of());
  }

  // --- Decomposition ---------------------------------------------------------------------

  private static List<SubQuery> decompose(String text, List<Intent> intents) {
    int end = text.length();
    Matcher tail = SHARED_TAIL.matcher(text);
    boolean sharedTail = tail.find();
    if (sharedTail) {
      end = tail.start();
    }
    List<int[]> steps = split(text, 0, end, SEQUENTIAL);
    List<SubQuery> subqueries = new ArrayList<>();
    List<String> previousStep = List.of();
    for (int[] step : steps) {
      List<int[]> items = split(text, step[0], step[1], PARALLEL);
      List<String> currentStep = new ArrayList<>();
      for (int[] item : items) {
        String id = "s" + (subqueries.size() + 1);
        String objective = text.substring(item[0], item[1]).strip();
        subqueries.add(
            new SubQuery(
                id,
                objective,
                intentsFor(intents, item, step, steps.getFirst()),
                List.of(),
                previousStep,
                true));
        currentStep.add(id);
      }
      previousStep = List.copyOf(currentStep);
    }
    return subqueries.size() >= 2 ? List.copyOf(subqueries) : List.of();
  }

  private static List<int[]> split(String text, int from, int to, Pattern separator) {
    List<int[]> parts = new ArrayList<>();
    Matcher m = separator.matcher(text).region(from, to);
    int start = from;
    while (m.find()) {
      if (!text.substring(start, m.start()).isBlank()) {
        parts.add(new int[] {start, m.start()});
      }
      start = m.end();
    }
    if (!text.substring(start, to).isBlank()) {
      parts.add(new int[] {start, to});
    }
    return parts;
  }

  /** Intents inside the item, else those of its step, else the request's leading intents. */
  private static List<String> intentsFor(
      List<Intent> intents, int[] item, int[] step, int[] firstStep) {
    for (int[] range : List.of(item, step, firstStep)) {
      List<String> ids =
          intents.stream()
              .filter(
                  i ->
                      i.span()
                          .filter(s -> s.start() >= range[0] && s.end() <= range[1])
                          .isPresent())
              .map(Intent::id)
              .toList();
      if (!ids.isEmpty()) {
        return ids;
      }
    }
    return intents.stream().map(Intent::id).toList();
  }

  // --- Assessment ------------------------------------------------------------------------

  private static QueryAssessment assess(
      boolean hasSubject,
      Optional<LanguageTag> language,
      Set<String> enabledDomains,
      List<String> domainHints) {
    List<String> unknownDomains =
        domainHints.stream().filter(d -> !enabledDomains.contains(d)).toList();
    String lang = language.map(LanguageTag::primaryLanguage).orElse("en");
    if (!domainHints.isEmpty() && unknownDomains.size() == domainHints.size()) {
      return new QueryAssessment(
          QueryAssessment.Status.OUT_OF_DOMAIN,
          List.of(QueryAssessment.Issue.DOMAIN_NOT_ENABLED),
          List.of(question(QueryAssessment.Issue.DOMAIN_NOT_ENABLED, lang, enabledDomains)));
    }
    if (!hasSubject) {
      return new QueryAssessment(
          QueryAssessment.Status.NEEDS_CLARIFICATION,
          List.of(QueryAssessment.Issue.NO_SUBJECT),
          List.of(question(QueryAssessment.Issue.NO_SUBJECT, lang, enabledDomains)));
    }
    return QueryAssessment.clear();
  }

  static String question(QueryAssessment.Issue issue, String language, Set<String> enabledDomains) {
    String domains = String.join(", ", new java.util.TreeSet<>(enabledDomains));
    return switch (issue) {
      case NO_SUBJECT ->
          switch (language) {
            case "sv" ->
                "Vad letar du efter? Beskriv vilken sorts sak eller information du vill hitta.";
            case "fa" ->
                "دنبال چه چیزی هستید؟ لطفاً نوع کالا یا اطلاعاتی را که می‌خواهید مشخص کنید.";
            default ->
                "What are you looking for? Please describe the kind of item or information you"
                    + " want.";
          };
      case DOMAIN_NOT_ENABLED ->
          switch (language) {
            case "sv" ->
                "Det området är inte aktiverat. Tillgängliga områden: "
                    + domains
                    + ". Vilket menar du?";
            case "fa" -> "این حوزه فعال نیست. حوزه‌های فعال: " + domains + ". منظور شما کدام است؟";
            default ->
                "That area is not enabled here. Available areas: "
                    + domains
                    + ". Which one do you mean?";
          };
    };
  }
}
