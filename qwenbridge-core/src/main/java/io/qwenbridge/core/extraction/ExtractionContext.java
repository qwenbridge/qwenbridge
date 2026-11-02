package io.qwenbridge.core.extraction;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Everything extraction may use besides the text itself (QB-030, QB-031). Nothing here is guessed:
 * without a reference time relative windows stay unresolved, without a time zone calendar words
 * ("yesterday") are not resolved, and without a schema field names are only proposals.
 *
 * @param language detected or declared language (number separators, currency words)
 * @param locale request locale; its region can disambiguate currencies
 * @param referenceTime the instant relative expressions are resolved against (e.g. request time)
 * @param zone time zone for calendar expressions
 * @param schema schema used to bind values to fields
 */
public record ExtractionContext(
    Optional<LanguageTag> language,
    Optional<LanguageTag> locale,
    Optional<Instant> referenceTime,
    Optional<ZoneId> zone,
    Optional<DomainSchema> schema,
    EntityDictionary dictionary) {

  public ExtractionContext {
    language = language == null ? Optional.empty() : language;
    locale = locale == null ? Optional.empty() : locale;
    referenceTime = referenceTime == null ? Optional.empty() : referenceTime;
    zone = zone == null ? Optional.empty() : zone;
    schema = schema == null ? Optional.empty() : schema;
    dictionary = dictionary == null ? EntityDictionary.EMPTY : dictionary;
  }

  public static ExtractionContext none() {
    return new ExtractionContext(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        EntityDictionary.EMPTY);
  }

  boolean commaDecimal() {
    return Numerals.commaDecimal(language);
  }
}
