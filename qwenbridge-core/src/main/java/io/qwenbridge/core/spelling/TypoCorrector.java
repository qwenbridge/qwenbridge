package io.qwenbridge.core.spelling;

import io.qwenbridge.core.request.LanguageTag;
import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The stateless typo engine (QB-291). It never assumes a language: only configured languages with
 * an installed provider are used, a reliably detected language outside the configured set disables
 * correction for the query, and protected tokens are never touched. The original text is always
 * kept; corrections are applied only as the configured policy allows.
 */
public final class TypoCorrector {

  /** Language evidence for the query. {@code reliable} means a single, determined language. */
  public record Detection(Optional<LanguageTag> language, boolean reliable, boolean mixed) {
    public Detection {
      language = language == null ? Optional.empty() : language;
    }

    public static Detection none() {
      return new Detection(Optional.empty(), false, false);
    }
  }

  private TypoCorrector() {}

  /** Validates configured identifiers against BCP 47 and the installed providers (QB-291 A4). */
  public static List<LanguageSupport> languages(
      TypoSettings settings, List<CorrectionProvider> providers) {
    List<LanguageSupport> out = new ArrayList<>();
    for (String raw : settings.languages()) {
      Optional<LanguageTag> tag = LanguageTag.parse(raw).filter(t -> !t.isUndetermined());
      if (tag.isEmpty()) {
        out.add(
            new LanguageSupport(
                String.valueOf(raw), LanguageSupport.Status.INVALID_IDENTIFIER, List.of()));
        continue;
      }
      List<String> ids =
          providers.stream()
              .filter(p -> p.descriptor().languages().contains(tag.get()))
              .map(p -> p.descriptor().id())
              .toList();
      out.add(
          new LanguageSupport(
              tag.get().value(),
              ids.isEmpty()
                  ? LanguageSupport.Status.PROVIDER_UNAVAILABLE
                  : LanguageSupport.Status.SUPPORTED,
              ids));
    }
    return out;
  }

  public static CorrectionResult correct(
      String original,
      String normalized,
      TypoSettings settings,
      Detection detection,
      List<CorrectionProvider> providers) {
    List<LanguageSupport> support = languages(settings, providers);
    Optional<LanguageTag> detected = detection.language();
    CorrectionMode mode = settings.effectiveMode();
    if (!settings.enabled() || settings.mode() == CorrectionMode.DISABLED)
      return pass(
          CorrectionResult.Status.DISABLED,
          settings.mode(),
          original,
          normalized,
          support,
          detected,
          List.of(),
          "Typo correction is disabled.");
    if (settings.languages().isEmpty())
      return pass(
          CorrectionResult.Status.UNCONFIGURED,
          CorrectionMode.DISABLED,
          original,
          normalized,
          support,
          detected,
          List.of(),
          "No language is configured for typo correction; nothing is assumed.");
    List<LanguageTag> enabled =
        support.stream()
            .filter(s -> s.status() == LanguageSupport.Status.SUPPORTED)
            .map(s -> new LanguageTag(s.configured()))
            .toList();
    if (enabled.isEmpty())
      return pass(
          CorrectionResult.Status.NO_PROVIDER,
          mode,
          original,
          normalized,
          support,
          detected,
          List.of(),
          "No installed provider supports the configured languages.");
    List<ProtectedTokens.Token> tokens = ProtectedTokens.tokenize(normalized);
    List<String> explanations = new ArrayList<>();
    List<LanguageTag> active = enabled;
    if (detection.reliable() && !detection.mixed() && detected.isPresent()) {
      String primary = detected.get().primaryLanguage();
      active = enabled.stream().filter(t -> t.primaryLanguage().equals(primary)).toList();
      if (active.isEmpty()) {
        // Typos themselves mislead detectors ("blak shoes" reads as Afrikaans). The query's own
        // known words are stronger evidence: use an enabled language that knows at least half of
        // them; otherwise the query is not in an enabled language and is left alone.
        Optional<LanguageTag> evidence = vocabularyEvidence(tokens, enabled, providers);
        if (evidence.isEmpty())
          return pass(
              CorrectionResult.Status.LANGUAGE_NOT_ENABLED,
              mode,
              original,
              normalized,
              support,
              detected,
              List.of(),
              "The query is in "
                  + detected.get().value()
                  + ", which is not enabled for typo correction.");
        active = List.of(evidence.get());
        explanations.add(
            "The detector reported "
                + detected.get().value()
                + ", but most known words are "
                + evidence.get().value()
                + "; "
                + evidence.get().value()
                + " is used.");
      } else if (enabled.size() > 1) {
        // The detected language is enabled, but short queries are often misdetected ("red wintr
        // jaket" reads as Swedish). Another enabled language that knows at least half of the words
        // and more of them than the detected language does is the better evidence (QB-295).
        Optional<LanguageTag> evidence = vocabularyEvidence(tokens, enabled, providers);
        if (evidence.isPresent()
            && !evidence.get().primaryLanguage().equals(primary)
            && known(tokens, evidence.get(), providers)
                > known(tokens, active.getFirst(), providers)) {
          explanations.add(
              "The detector reported "
                  + detected.get().value()
                  + ", but more known words are "
                  + evidence.get().value()
                  + "; "
                  + evidence.get().value()
                  + " is used.");
          active = List.of(evidence.get());
        }
      }
    }

    int protectedCount =
        (int) tokens.stream().filter(t -> t.kind() == ProtectedTokens.Kind.PROTECTED).count();
    List<CorrectionResult.TokenCorrection> corrections = new ArrayList<>();
    Map<Integer, String> context = new LinkedHashMap<>();
    for (int i = 0; i < tokens.size(); i++) context.put(i, tokens.get(i).text());
    for (int i = 0; i < tokens.size(); i++) {
      ProtectedTokens.Token token = tokens.get(i);
      if (token.kind() != ProtectedTokens.Kind.WORD) continue;
      Set<UnicodeScript> scripts = scripts(token.text());
      List<Map.Entry<LanguageTag, CorrectionProvider>> pairs = new ArrayList<>();
      boolean known = false;
      for (LanguageTag language : active)
        for (CorrectionProvider p : providers) {
          if (!p.descriptor().languages().contains(language)
              || !p.descriptor().scripts().containsAll(scripts)) continue;
          if (p.knows(language, token.text())) known = true;
          pairs.add(Map.entry(language, p));
        }
      // A word that any enabled language knows is not a typo, whatever the detector chose: with
      // en and sv enabled, the English "red" must not become the Swedish "röd" when a query is
      // misdetected as Swedish (QB-294 runtime finding).
      if (!known)
        for (LanguageTag language : enabled)
          for (CorrectionProvider p : providers)
            if (p.descriptor().languages().contains(language)
                && p.descriptor().scripts().containsAll(scripts)
                && p.knows(language, token.text())) known = true;
      if (known || pairs.isEmpty()) continue;
      int length = token.text().codePointCount(0, token.text().length());
      double maxDistance =
          mode == CorrectionMode.AGGRESSIVE ? (length <= 4 ? 1.0 : 2.0) : (length >= 8 ? 2.0 : 1.0);
      String previous = i > 0 ? context.get(i - 1) : null;
      String next = i + 1 < tokens.size() ? context.get(i + 1) : null;
      List<Scored> scored = new ArrayList<>();
      for (var pair : pairs)
        for (CorrectionProvider.Candidate c :
            pair.getValue().candidates(pair.getKey(), token.text(), maxDistance)) {
          boolean contextual =
              (previous != null && pair.getValue().pair(pair.getKey(), previous, c.form()))
                  || (next != null && pair.getValue().pair(pair.getKey(), c.form(), next));
          scored.add(new Scored(c, score(c, contextual), contextual));
        }
      if (scored.isEmpty()) continue;
      scored.sort(
          Comparator.comparingDouble(Scored::score)
              .reversed()
              .thenComparing(s -> s.candidate().form()));
      // The same word offered by two providers or languages is one candidate.
      List<Scored> distinct = new ArrayList<>();
      for (Scored s : scored)
        if (distinct.stream()
            .noneMatch(
                d -> Lexicon.key(d.candidate().form()).equals(Lexicon.key(s.candidate().form()))))
          distinct.add(s);
      Scored best = distinct.getFirst();
      double margin = distinct.size() > 1 ? best.score() - distinct.get(1).score() : 1.0;
      double confidence = Math.min(0.99, best.score() * Math.min(1.0, 0.6 + 4 * margin));
      String replacement = casing(token.text(), best.candidate().form());
      boolean withinSafeEdits =
          best.candidate().distance() <= 1.0 || (length >= 8 && best.candidate().distance() <= 2.0);
      boolean applied;
      String reason;
      if (!mode.applies()) {
        applied = false;
        reason = "suggestion only (policy " + mode + ")";
      } else if (mode == CorrectionMode.SAFE_AUTO_CORRECT
          && (!withinSafeEdits || confidence < settings.safeThreshold())) {
        applied = false;
        reason =
            String.format(
                Locale.ROOT,
                "confidence %.2f below the safe threshold %.2f",
                confidence,
                settings.safeThreshold());
      } else if (mode == CorrectionMode.AGGRESSIVE && confidence < settings.aggressiveThreshold()) {
        applied = false;
        reason =
            String.format(
                Locale.ROOT,
                "confidence %.2f below the aggressive threshold %.2f",
                confidence,
                settings.aggressiveThreshold());
      } else {
        applied = true;
        reason =
            String.format(
                Locale.ROOT,
                "%s: edit distance %.1f%s%s",
                best.candidate().language().value(),
                best.candidate().distance(),
                best.contextual() ? ", fits the neighbouring word" : "",
                best.candidate().domain() ? ", domain vocabulary" : "");
        context.put(i, replacement);
      }
      corrections.add(
          new CorrectionResult.TokenCorrection(
              token.text(),
              token.start(),
              token.end(),
              best.candidate().language().value(),
              distinct.stream()
                  .limit(3)
                  .map(
                      s ->
                          new CorrectionResult.Suggestion(
                              casing(token.text(), s.candidate().form()),
                              s.candidate().language().value(),
                              round(s.score())))
                  .toList(),
              replacement,
              round(confidence),
              applied,
              reason));
      explanations.add("\"" + token.text() + "\" -> \"" + replacement + "\": " + reason);
    }
    String corrected = rebuild(normalized, corrections);
    CorrectionResult.Status status =
        corrections.stream().anyMatch(CorrectionResult.TokenCorrection::applied)
            ? CorrectionResult.Status.CORRECTED
            : corrections.isEmpty()
                ? CorrectionResult.Status.NO_CHANGES
                : CorrectionResult.Status.SUGGESTED;
    if (protectedCount > 0)
      explanations.add(protectedCount + " protected token(s) left unchanged.");
    return new CorrectionResult(
        status,
        mode,
        original,
        normalized,
        corrected,
        support,
        detected,
        active.stream().map(LanguageTag::value).toList(),
        corrections,
        protectedCount,
        explanations);
  }

  /** The enabled language whose providers know the most (at least half) of the plain words. */
  private static long known(
      List<ProtectedTokens.Token> tokens,
      LanguageTag language,
      List<CorrectionProvider> providers) {
    return tokens.stream()
        .filter(t -> t.kind() == ProtectedTokens.Kind.WORD)
        .filter(
            t ->
                providers.stream()
                    .anyMatch(
                        p ->
                            p.descriptor().languages().contains(language)
                                && p.knows(language, t.text())))
        .count();
  }

  private static Optional<LanguageTag> vocabularyEvidence(
      List<ProtectedTokens.Token> tokens,
      List<LanguageTag> enabled,
      List<CorrectionProvider> providers) {
    List<String> words =
        tokens.stream()
            .filter(t -> t.kind() == ProtectedTokens.Kind.WORD)
            .map(ProtectedTokens.Token::text)
            .toList();
    if (words.isEmpty()) return Optional.empty();
    LanguageTag best = null;
    long bestKnown = 0;
    for (LanguageTag language : enabled) {
      long known =
          words.stream()
              .filter(
                  w ->
                      providers.stream()
                          .anyMatch(
                              p ->
                                  p.descriptor().languages().contains(language)
                                      && p.knows(language, w)))
              .count();
      if (known > bestKnown) {
        best = language;
        bestKnown = known;
      }
    }
    return best != null && bestKnown * 2 >= words.size() ? Optional.of(best) : Optional.empty();
  }

  private record Scored(CorrectionProvider.Candidate candidate, double score, boolean contextual) {}

  private static double score(CorrectionProvider.Candidate c, boolean contextual) {
    double base =
        c.distance() <= 0.6
            ? 0.9
            : c.distance() <= 0.8
                ? 0.86
                : c.distance() <= 1.0 ? 0.8 : c.distance() <= 1.6 ? 0.66 : 0.56;
    return Math.min(
        0.99, base + (contextual ? 0.06 : 0) + 0.04 * c.frequency() + (c.domain() ? 0.02 : 0));
  }

  private static double round(double value) {
    return Math.round(value * 1000) / 1000.0;
  }

  private static Set<UnicodeScript> scripts(String word) {
    EnumSet<UnicodeScript> out = EnumSet.noneOf(UnicodeScript.class);
    word.codePoints().filter(Character::isLetter).mapToObj(UnicodeScript::of).forEach(out::add);
    return out;
  }

  /** Keeps a canonical mixed-case form (iPhone); otherwise follows the token's capitalization. */
  static String casing(String token, String form) {
    if (form.codePoints().anyMatch(Character::isUpperCase)) return form;
    int first = token.codePointAt(0);
    if (Character.isUpperCase(first)
        && token.codePoints().skip(1).noneMatch(Character::isUpperCase)) {
      int f = form.codePointAt(0);
      return new StringBuilder()
          .appendCodePoint(Character.toTitleCase(f))
          .append(form.substring(Character.charCount(f)))
          .toString();
    }
    return form;
  }

  private static String rebuild(String text, List<CorrectionResult.TokenCorrection> corrections) {
    StringBuilder out = new StringBuilder();
    int at = 0;
    for (CorrectionResult.TokenCorrection c : corrections) {
      if (!c.applied()) continue;
      out.append(text, at, c.start()).append(c.replacement());
      at = c.end();
    }
    return out.append(text.substring(at)).toString();
  }

  private static CorrectionResult pass(
      CorrectionResult.Status status,
      CorrectionMode mode,
      String original,
      String normalized,
      List<LanguageSupport> support,
      Optional<LanguageTag> detected,
      List<String> active,
      String why) {
    return new CorrectionResult(
        status,
        mode,
        original,
        normalized,
        normalized,
        support,
        detected,
        active,
        List.of(),
        0,
        List.of(why));
  }
}
