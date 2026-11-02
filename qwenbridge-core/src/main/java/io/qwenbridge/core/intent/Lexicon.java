package io.qwenbridge.core.intent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic multilingual cue lists for English, Swedish and Persian (QB-023, QB-024, QB-026).
 * Other languages still work through content-word detection, without intent cue words. Cues are
 * matched on letter boundaries, so "find" never matches inside "findings".
 */
final class Lexicon {

  private Lexicon() {}

  /** A cue occurrence in the text, with UTF-16 offsets. */
  record Match(int start, int end, String text) {}

  static final Map<String, List<String>> INTENT_CUES = intentCues();

  private static Map<String, List<String>> intentCues() {
    Map<String, List<String>> cues = new LinkedHashMap<>();
    cues.put(
        IntentTaxonomy.COMPARE,
        List.of(
            "compare",
            "versus",
            "vs",
            "difference between",
            "jämför",
            "jämföra",
            "skillnad mellan",
            "مقایسه",
            "تفاوت"));
    cues.put(
        IntentTaxonomy.INVESTIGATE,
        List.of(
            "investigate",
            "diagnose",
            "troubleshoot",
            "root cause",
            "felsök",
            "felsöka",
            "undersök",
            "عیب‌یابی",
            "عیبیابی",
            "علت اصلی"));
    cues.put(
        IntentTaxonomy.ANALYZE,
        List.of(
            "analyze",
            "analyse",
            "identify",
            "rank",
            "classify",
            "detect",
            "analysera",
            "identifiera",
            "تحلیل",
            "شناسایی",
            "رتبه‌بندی"));
    cues.put(
        IntentTaxonomy.EXPLAIN,
        List.of("explain", "why", "how does", "how do", "förklara", "varför", "چرا", "توضیح"));
    cues.put(
        IntentTaxonomy.REFINE,
        List.of(
            "instead",
            "only the",
            "cheaper ones",
            "show only",
            "istället",
            "bara de",
            "به جای",
            "به‌جای",
            "فقط"));
    cues.put(
        IntentTaxonomy.NAVIGATE,
        List.of("go to", "open the", "take me to", "gå till", "öppna", "برو به", "باز کن"));
    cues.put(
        IntentTaxonomy.SEARCH,
        List.of(
            "find",
            "search",
            "search for",
            "show",
            "show me",
            "list",
            "get",
            "looking for",
            "i want",
            "i need",
            "hitta",
            "sök",
            "söker",
            "visa",
            "jag vill ha",
            "jag behöver",
            "پیدا کن",
            "جستجو",
            "نشان بده",
            "می‌خوام",
            "میخوام",
            "می خوام",
            "می‌خواهم",
            "می خواهم",
            "لازم دارم"));
    return Map.copyOf(cues);
  }

  /** Words that carry no subject on their own (QB-026: "Find the best option"). */
  static final Set<String> VAGUE =
      Set.of(
          "alternativet",
          "alternativen",
          "sak",
          "saken",
          "grej",
          "grejen",
          "گزینه‌ها",
          "best",
          "good",
          "great",
          "nice",
          "option",
          "options",
          "alternative",
          "alternatives",
          "something",
          "anything",
          "thing",
          "things",
          "stuff",
          "one",
          "ones",
          "recommend",
          "recommendation",
          "bästa",
          "bra",
          "alternativ",
          "något",
          "någon",
          "بهترین",
          "خوب",
          "گزینه",
          "چیزی",
          "یکی",
          "پیشنهاد");

  static final Set<String> STOPWORDS =
      Set.of(
          "a", "an", "the", "for", "of", "to", "in", "on", "me", "my", "please", "some", "with",
          "and", "or", "is", "are", "what", "which", "that", "this", "it", "en", "ett", "den",
          "det", "de", "för", "till", "av", "med", "och", "eller", "som", "mig", "jag", "är", "یک",
          "را", "برای", "به", "از", "با", "و", "یا", "که", "این", "آن", "من", "است");

  static final List<String> STRUCTURED_CUES =
      List.of(
          "under",
          "below",
          "above",
          "over",
          "less than",
          "more than",
          "at least",
          "at most",
          "between",
          "cheaper than",
          "after",
          "before",
          "during",
          "since",
          "within",
          "last",
          "över",
          "mindre än",
          "mer än",
          "minst",
          "högst",
          "mellan",
          "senaste",
          "زیر",
          "بالای",
          "کمتر از",
          "بیشتر از",
          "حداقل",
          "حداکثر",
          "بین",
          "قبل از",
          "بعد از");

  static final List<String> NEGATION_CUES =
      List.of(
          "not",
          "no",
          "without",
          "excluding",
          "except",
          "inte",
          "utan",
          "ej",
          "förutom",
          "بدون",
          "غیر",
          "به جز",
          "نه");

  static final List<String> SEMANTIC_CUES =
      List.of(
          "comfortable",
          "lightweight",
          "elegant",
          "suitable",
          "similar",
          "stylish",
          "durable",
          "premium",
          "minimalist",
          "cozy",
          "suspicious",
          "abnormal",
          "unusual",
          "anomalous",
          "increased",
          "spiking",
          "slow",
          "bekväm",
          "bekväma",
          "lätt",
          "lätta",
          "elegant",
          "passar",
          "passande",
          "liknande",
          "snygg",
          "misstänkt",
          "ovanlig",
          "راحت",
          "سبک",
          "شیک",
          "مناسب",
          "مشابه",
          "باکیفیت",
          "مشکوک",
          "غیرعادی");

  private static final Pattern COMPARATOR = Pattern.compile("[<>≤≥]=?|!=");
  private static final Pattern NUMBER_WITH_UNIT =
      Pattern.compile(
          "[\\p{Nd}]+(?:[.,][\\p{Nd}]+)?\\s?(?:%|[\\p{Sc}]|kr|sek|eur|usd|kg|g|ms|s|h|gb|mb|mib|gib)(?![\\p{L}])",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  static boolean hasExactIdentifier(String text) {
    return io.qwenbridge.core.text.Identifiers.containsAny(text);
  }

  static boolean hasComparatorOrMeasure(String text) {
    return COMPARATOR.matcher(text).find() || NUMBER_WITH_UNIT.matcher(text).find();
  }

  /** All occurrences of any phrase, on letter boundaries, case-insensitively. */
  static List<Match> find(String text, List<String> phrases) {
    List<Match> matches = new ArrayList<>();
    for (String phrase : phrases) {
      Matcher m = boundaryPattern(phrase).matcher(text);
      while (m.find()) {
        matches.add(new Match(m.start(), m.end(), m.group()));
      }
    }
    return matches;
  }

  private static final Map<String, Pattern> PATTERNS =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static Pattern boundaryPattern(String phrase) {
    return PATTERNS.computeIfAbsent(
        phrase,
        p -> {
          // Letters may be joined by ZWNJ in Persian; treat ZWNJ as part of a word.
          String body = Pattern.quote(p).replace(" ", "\\E[\\s\\u200c]+\\Q");
          return Pattern.compile(
              "(?<![\\p{L}\\p{M}\\u200c])" + body + "(?![\\p{L}\\p{M}\\u200c])",
              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        });
  }

  /** Lower-cased letter words (with joined marks/ZWNJ) in order of appearance. */
  static List<Match> words(String text) {
    List<Match> words = new ArrayList<>();
    Matcher m = WORD.matcher(text);
    while (m.find()) {
      words.add(new Match(m.start(), m.end(), m.group().toLowerCase(Locale.ROOT)));
    }
    return words;
  }

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}\\u200c]*");
}
