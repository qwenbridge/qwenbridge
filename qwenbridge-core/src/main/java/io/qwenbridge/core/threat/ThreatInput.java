package io.qwenbridge.core.threat;

import io.qwenbridge.core.request.LanguageTag;
import java.util.Objects;
import java.util.Optional;

/**
 * A text to inspect and where it came from (QB-064, QB-067).
 *
 * @param language language of the text when known; detectors must not require it
 */
public record ThreatInput(String text, TrustZone zone, Optional<LanguageTag> language) {

  public ThreatInput {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(zone, "zone must not be null");
    language = language == null ? Optional.empty() : language;
  }

  public static ThreatInput of(String text, TrustZone zone) {
    return new ThreatInput(text, zone, Optional.empty());
  }
}
