package io.qwenbridge.core.translation;

import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Skipped;
import io.qwenbridge.core.translation.TranslationResult.Translated;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Contract conformance for {@link TranslationProvider} implementations (QB-021). It checks the
 * shape of the provider's behaviour, which must hold for every provider and can be tested offline
 * with fixtures: identity, capabilities, typed results, the requested target language, and no
 * exceptions.
 *
 * <p>It deliberately does not judge translation quality. Whether a real model preserves meaning is
 * measured separately, by running {@link PreservationChecker} over a corpus against the live
 * provider; that result is a quality measurement, not a contract.
 */
public final class TranslationConformance {

  private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9.-]{0,63}$");

  public record Violation(String code, String detail) {}

  private TranslationConformance() {}

  public static List<Violation> verify(
      TranslationProvider provider, List<TranslationRequest> probes) {
    List<Violation> violations = new ArrayList<>();
    String id = provider.id();
    if (id == null || !ID.matcher(id).matches()) {
      violations.add(new Violation("INVALID_ID", "id must match " + ID.pattern()));
    }
    if (provider.capabilities() == null) {
      violations.add(new Violation("NO_CAPABILITIES", "capabilities must be declared"));
      return violations;
    }
    for (int i = 0; i < probes.size(); i++) {
      TranslationRequest probe = probes.get(i);
      String at = "probe " + i;
      TranslationResult result;
      try {
        result = provider.translate(probe);
      } catch (RuntimeException e) {
        violations.add(new Violation("THREW", at + ": " + e.getClass().getSimpleName()));
        continue;
      }
      switch (result) {
        case null -> violations.add(new Violation("NULL_RESULT", at));
        case Skipped s ->
            violations.add(new Violation("SKIPPED", at + ": skipping is decided by the stage"));
        case Failed f -> {
          if (f.providerId().isPresent() && !f.providerId().get().equals(id)) {
            violations.add(new Violation("PROVIDER_ID_MISMATCH", at));
          }
        }
        case Translated t -> {
          if (!t.providerId().equals(id)) {
            violations.add(new Violation("PROVIDER_ID_MISMATCH", at));
          }
          if (!t.target().primaryLanguage().equals(probe.target().primaryLanguage())) {
            violations.add(new Violation("WRONG_TARGET", at));
          }
          if (!probe.source().isUndetermined()
              && !t.source().primaryLanguage().equals(probe.source().primaryLanguage())) {
            violations.add(new Violation("SOURCE_CHANGED", at));
          }
          if (t.text().isBlank()) {
            violations.add(new Violation("BLANK_TRANSLATION", at));
          }
        }
      }
    }
    return violations;
  }
}
