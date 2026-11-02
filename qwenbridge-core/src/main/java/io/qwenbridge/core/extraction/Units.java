package io.qwenbridge.core.extraction;

import io.qwenbridge.core.request.LanguageTag;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Measurement units and currencies (QB-030). A currency is resolved only when the text or the
 * request context justifies it; ambiguous words and symbols (krona/kronor, dollar, $, ¥) never
 * default to one country's currency. Conversion rates are never assumed.
 */
public final class Units {

  private Units() {}

  /** Canonical unit symbols by lower-case surface form. */
  private static final Map<String, String> UNITS =
      Map.ofEntries(
          Map.entry("%", "%"),
          Map.entry("percent", "%"),
          Map.entry("procent", "%"),
          Map.entry("درصد", "%"),
          Map.entry("kg", "kg"),
          Map.entry("kilo", "kg"),
          Map.entry("kilogram", "kg"),
          Map.entry("کیلو", "kg"),
          Map.entry("g", "g"),
          Map.entry("gram", "g"),
          Map.entry("گرم", "g"),
          Map.entry("mg", "mg"),
          Map.entry("lb", "lb"),
          Map.entry("lbs", "lb"),
          Map.entry("km", "km"),
          Map.entry("m", "m"),
          Map.entry("cm", "cm"),
          Map.entry("mm", "mm"),
          Map.entry("inch", "in"),
          Map.entry("inches", "in"),
          Map.entry("tum", "in"),
          Map.entry("اینچ", "in"),
          Map.entry("ms", "ms"),
          Map.entry("s", "s"),
          Map.entry("sec", "s"),
          Map.entry("seconds", "s"),
          Map.entry("min", "min"),
          Map.entry("minutes", "min"),
          Map.entry("h", "h"),
          Map.entry("hours", "h"),
          Map.entry("gb", "GB"),
          Map.entry("mb", "MB"),
          Map.entry("tb", "TB"),
          Map.entry("gib", "GiB"),
          Map.entry("mib", "MiB"),
          Map.entry("ghz", "GHz"),
          Map.entry("mhz", "MHz"),
          Map.entry("mah", "mAh"),
          Map.entry("w", "W"),
          Map.entry("v", "V"),
          Map.entry("°c", "°C"),
          Map.entry("rps", "rps"),
          Map.entry("rpm", "rpm"));

  private static final Set<String> ISO_4217 =
      Currency.getAvailableCurrencies().stream()
          .map(Currency::getCurrencyCode)
          .collect(Collectors.toUnmodifiableSet());

  /** Unambiguous currency words and symbols. */
  private static final Map<String, String> CURRENCY_WORDS =
      Map.ofEntries(
          Map.entry("€", "EUR"),
          Map.entry("euro", "EUR"),
          Map.entry("euros", "EUR"),
          Map.entry("یورو", "EUR"),
          Map.entry("£", "GBP"),
          Map.entry("pound", "GBP"),
          Map.entry("pounds", "GBP"),
          Map.entry("₹", "INR"),
          Map.entry("rupees", "INR"),
          Map.entry("₺", "TRY"),
          Map.entry("lira", "TRY"),
          Map.entry("﷼", "IRR"),
          Map.entry("ریال", "IRR"),
          Map.entry("تومان", "IRR"));

  /** Ambiguous currency words: resolved only through language/locale. */
  private static final Set<String> KRONA_WORDS =
      Set.of("kr", "kr.", "krona", "kronor", "krone", "kroner", "króna", "krónur", "کرون");

  private static final Set<String> DOLLAR_WORDS = Set.of("$", "dollar", "dollars", "دلار");

  public static Optional<String> unit(String surface) {
    return surface == null
        ? Optional.empty()
        : Optional.ofNullable(UNITS.get(surface.toLowerCase(Locale.ROOT)));
  }

  /** Whether the surface form denotes some currency, resolved or not. */
  public static boolean isCurrency(String surface) {
    if (surface == null) {
      return false;
    }
    String lower = surface.toLowerCase(Locale.ROOT);
    return ISO_4217.contains(surface.toUpperCase(Locale.ROOT))
            && surface.length() == 3
            && surface.equals(surface.toUpperCase(Locale.ROOT))
        || CURRENCY_WORDS.containsKey(lower)
        || KRONA_WORDS.contains(lower)
        || DOLLAR_WORDS.contains(lower)
        || "¥".equals(surface);
  }

  /**
   * Resolves a currency surface form to ISO 4217, or empty when ambiguous.
   *
   * @param language request language; {@code sv}/{@code nb}/{@code nn}/{@code no}/{@code da}/
   *     {@code is} disambiguate krona/krone
   * @param locale request locale; its region disambiguates krona and dollar (e.g. sv-SE, en-US)
   */
  public static Optional<String> currency(
      String surface, Optional<LanguageTag> language, Optional<LanguageTag> locale) {
    if (surface == null) {
      return Optional.empty();
    }
    String upper = surface.toUpperCase(Locale.ROOT);
    if (surface.length() == 3 && surface.equals(upper) && ISO_4217.contains(upper)) {
      return Optional.of(upper);
    }
    String lower = surface.toLowerCase(Locale.ROOT);
    if (CURRENCY_WORDS.containsKey(lower)) {
      return Optional.of(CURRENCY_WORDS.get(lower));
    }
    String region = locale.map(l -> l.toLocale().getCountry()).orElse("");
    String lang = language.map(LanguageTag::primaryLanguage).orElse("");
    if (KRONA_WORDS.contains(lower)) {
      return switch (region) {
        case "SE" -> Optional.of("SEK");
        case "NO" -> Optional.of("NOK");
        case "DK" -> Optional.of("DKK");
        case "IS" -> Optional.of("ISK");
        default ->
            switch (lang) {
              case "sv" -> Optional.of("SEK");
              case "nb", "nn", "no" -> Optional.of("NOK");
              case "da" -> Optional.of("DKK");
              case "is" -> Optional.of("ISK");
              default -> Optional.empty();
            };
      };
    }
    if (DOLLAR_WORDS.contains(lower)) {
      return switch (region) {
        case "US" -> Optional.of("USD");
        case "CA" -> Optional.of("CAD");
        case "AU" -> Optional.of("AUD");
        case "NZ" -> Optional.of("NZD");
        default -> Optional.empty();
      };
    }
    return Optional.empty();
  }
}
