package io.qwenbridge.language;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.language.LanguageDetection;
import io.qwenbridge.core.language.LanguageDetector;
import io.qwenbridge.core.language.ScriptHeuristicLanguageDetector;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.request.TextDirection;
import io.qwenbridge.core.text.ScriptSegment;
import io.qwenbridge.core.text.ScriptSegmenter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Language detection for the pipeline (QB-004, QB-008, QB-013).
 *
 * <p>Decision order for a text: unambiguous script rules (heuristic), then the Lingua model on the
 * dominant non-Latin part, then the model on the whole text. When none of them is confident the
 * answer is {@code und} — there is no default language. In particular short Latin-script text is
 * not assumed to be English. If the model is disabled or fails, the deterministic {@link
 * ScriptHeuristicLanguageDetector} is used.
 */
@ApplicationScoped
public class LanguageService {

  private static final Logger LOG = Logger.getLogger(LanguageService.class);

  /** Segments with fewer letters are too short for a meaningful language decision. */
  static final int MIN_SEGMENT_LETTERS = 3;

  /** Minimum words (or letters in unspaced scripts) before the statistical model is consulted. */
  static final int MIN_MODEL_EVIDENCE = 4;

  private static final Pattern HINDI_MARKERS =
      Pattern.compile("(?<![\\p{L}\\p{M}])(?:मुझे|चाहिए|नहीं|जूते)(?![\\p{L}\\p{M}])");

  private final LanguageDetector model;
  private final LanguageDetector heuristic = new ScriptHeuristicLanguageDetector();
  private final boolean modelEnabled;
  private final Set<String> configured;

  /** Defaults: model enabled; configured languages en, fa, sv. */
  public LanguageService() {
    this(true, List.of("en", "fa", "sv"));
  }

  @Inject
  public LanguageService(LanguageConfig config) {
    this(config.modelEnabled(), config.configured());
  }

  public LanguageService(boolean modelEnabled, List<String> configuredLanguages) {
    this.modelEnabled = modelEnabled;
    this.model = modelEnabled ? new LinguaDetector() : null;
    this.configured =
        configuredLanguages.stream()
            .map(
                tag ->
                    LanguageTag.parse(tag)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "qwenbridge.language.configured has an invalid tag: " + tag)))
            .map(LanguageTag::primaryLanguage)
            .collect(Collectors.toUnmodifiableSet());
  }

  /** Legacy form: primary language code or {@code unknown}. */
  public String detect(String text) {
    return analyze(text, Optional.empty()).primaryLanguage();
  }

  public LanguageAnalysis analyze(String text, Optional<LanguageTag> declared) {
    String safe = text == null ? "" : text;
    List<LanguageAnalysis.SegmentLanguage> segments = new ArrayList<>();
    List<ScriptSegment> lettered =
        ScriptSegmenter.segment(safe).stream()
            .filter(s -> s.script() != UnicodeScript.COMMON)
            .toList();

    if (lettered.size() <= 1) {
      LanguageDetection whole = detectWhole(safe);
      for (ScriptSegment s : lettered) {
        segments.add(segment(s, whole));
      }
      return result(whole, declared, whole.legacyCode(), false, segments, safe);
    }

    Map<String, Integer> lettersByLanguage = new LinkedHashMap<>();
    Map<UnicodeScript, Boolean> substantialByScript = new LinkedHashMap<>();
    Map<UnicodeScript, Boolean> determinedByScript = new LinkedHashMap<>();
    for (ScriptSegment s : lettered) {
      substantialByScript.merge(s.script(), isSubstantial(s.text()), Boolean::logicalOr);
      int letters = letters(s.text());
      LanguageDetection detection =
          letters >= MIN_SEGMENT_LETTERS
              ? detectWhole(s.text())
              : LanguageDetection.undetermined(ScriptHeuristicLanguageDetector.ID);
      segments.add(
          new LanguageAnalysis.SegmentLanguage(
              s.start(), s.end(), s.script().name(), letters, detection));
      if (detection.determined()) {
        lettersByLanguage.merge(detection.legacyCode(), letters, Integer::sum);
        determinedByScript.put(s.script(), true);
      }
    }

    // Mixed: two determined languages, or a determined segment plus a substantial segment in a
    // different script (that segment may be undetermined, e.g. a short English phrase). A single
    // brand word in another script is not substantial and does not make a sentence mixed.
    boolean otherSubstantialScript =
        !determinedByScript.isEmpty()
            && substantialByScript.entrySet().stream()
                .anyMatch(e -> e.getValue() && !determinedByScript.containsKey(e.getKey()));
    if (lettersByLanguage.size() >= 2 || otherSubstantialScript) {
      String primary =
          lettersByLanguage.entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(LanguageAnalysis.UNKNOWN);
      LanguageDetection overall = LanguageDetection.undetermined(detectorId());
      return result(overall, declared, primary, true, segments, safe);
    }
    LanguageDetection whole = detectWhole(safe);
    return result(whole, declared, whole.legacyCode(), false, segments, safe);
  }

  /** Language of a text taken as a whole; {@code und} when not confidently identifiable. */
  public LanguageDetection detectWhole(String text) {
    if (text == null || text.isBlank() || letters(text) == 0) {
      return LanguageDetection.undetermined(detectorId());
    }
    if (!modelEnabled) {
      return heuristic.detect(text);
    }
    try {
      if (HINDI_MARKERS.matcher(text).find()) {
        return new LanguageDetection(
            LanguageTag.parse("hi").orElseThrow(), Confidence.heuristic(), "hindi-markers-v1");
      }
      LanguageDetection script = heuristic.detect(text);
      if (script.determined()) {
        return script;
      }
      long latin = text.codePoints().filter(cp -> isLetter(cp, true)).count();
      long other = text.codePoints().filter(cp -> isLetter(cp, false)).count();
      if (other >= 4 && other >= latin) {
        // Brand or currency words must not outweigh the dominant non-Latin script.
        String nonLatin = text.replaceAll("\\p{IsLatin}+", " ");
        if (hasModelEvidence(nonLatin)) {
          LanguageDetection dominant = model.detect(nonLatin);
          if (dominant.determined()) {
            return dominant;
          }
        }
      }
      // The statistical model is unreliable on very short text (it confidently returns e.g. "fr"
      // for "table"), so short text stays undetermined unless a script rule decided above.
      return hasModelEvidence(text)
          ? model.detect(text)
          : LanguageDetection.undetermined(LinguaDetector.ID);
    } catch (RuntimeException e) {
      LOG.warnf(
          "Language model failed (%s); using script heuristics", e.getClass().getSimpleName());
      return heuristic.detect(text);
    }
  }

  private LanguageAnalysis result(
      LanguageDetection overall,
      Optional<LanguageTag> declared,
      String primary,
      boolean mixed,
      List<LanguageAnalysis.SegmentLanguage> segments,
      String text) {
    return new LanguageAnalysis(
        overall,
        declared,
        primary,
        mixed,
        segments,
        TextDirection.of(text),
        configured.contains(primary));
  }

  private static LanguageAnalysis.SegmentLanguage segment(
      ScriptSegment s, LanguageDetection detection) {
    return new LanguageAnalysis.SegmentLanguage(
        s.start(), s.end(), s.script().name(), letters(s.text()), detection);
  }

  private String detectorId() {
    return modelEnabled ? LinguaDetector.ID : ScriptHeuristicLanguageDetector.ID;
  }

  private static int letters(String text) {
    return (int) text.codePoints().filter(Character::isLetter).count();
  }

  /** Words are runs of letters (and their combining marks or joiners). */
  static int words(String text) {
    int words = 0;
    boolean inWord = false;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      i += Character.charCount(cp);
      boolean wordPart =
          Character.isLetter(cp)
              || Character.getType(cp) == Character.NON_SPACING_MARK
              || Character.getType(cp) == Character.COMBINING_SPACING_MARK
              || cp == 0x200C
              || cp == 0x200D;
      if (wordPart && !inWord) {
        words++;
      }
      inWord = wordPart;
    }
    return words;
  }

  /**
   * Scripts written without spaces between words; for them letters, not words, measure evidence.
   */
  private static boolean unspacedScript(int cp) {
    UnicodeScript script = UnicodeScript.of(cp);
    return script == UnicodeScript.HAN
        || script == UnicodeScript.HIRAGANA
        || script == UnicodeScript.KATAKANA
        || script == UnicodeScript.THAI
        || script == UnicodeScript.LAO
        || script == UnicodeScript.KHMER
        || script == UnicodeScript.MYANMAR;
  }

  private static boolean mostlyUnspaced(String text) {
    long letters = text.codePoints().filter(Character::isLetter).count();
    long unspaced =
        text.codePoints().filter(cp -> Character.isLetter(cp) && unspacedScript(cp)).count();
    return letters > 0 && unspaced * 2 >= letters;
  }

  /** Enough text for the statistical model: 4 words, or 4 letters in unspaced scripts. */
  static boolean hasModelEvidence(String text) {
    return mostlyUnspaced(text)
        ? letters(text) >= MIN_MODEL_EVIDENCE
        : words(text) >= MIN_MODEL_EVIDENCE;
  }

  /** A segment big enough to count as content in its own right (2 words, or 4 unspaced letters). */
  static boolean isSubstantial(String text) {
    return mostlyUnspaced(text) ? letters(text) >= 4 : words(text) >= 2;
  }

  private static boolean isLetter(int cp, boolean latin) {
    return Character.isLetter(cp)
        && (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) == latin;
  }

  /**
   * Lingua-backed statistical detector. In low-accuracy mode Lingua's confidence values were
   * observed to be saturated (1.0 even for wrong answers such as "tl" for "cheap office chair"), so
   * no number is reported: results carry {@code MODEL_DECISION}, never a fake score.
   */
  static final class LinguaDetector implements LanguageDetector {

    static final String ID = "lingua-low-accuracy-v1";

    private final com.github.pemistahl.lingua.api.LanguageDetector detector =
        LanguageDetectorBuilder.fromAllLanguages()
            .withLowAccuracyMode()
            .withMinimumRelativeDistance(0.05)
            .build();

    @Override
    public String id() {
      return ID;
    }

    @Override
    public LanguageDetection detect(String text) {
      Language language = detector.detectLanguageOf(text);
      if (language == Language.UNKNOWN) {
        return LanguageDetection.undetermined(ID);
      }
      Optional<LanguageTag> tag =
          LanguageTag.parse(language.getIsoCode639_1().toString().toLowerCase(Locale.ROOT));
      if (tag.isEmpty()) {
        return LanguageDetection.undetermined(ID);
      }
      return new LanguageDetection(tag.get(), Confidence.modelDecision(), ID);
    }
  }
}
