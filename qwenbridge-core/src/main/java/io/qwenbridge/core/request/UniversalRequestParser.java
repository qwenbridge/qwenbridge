package io.qwenbridge.core.request;

import io.qwenbridge.core.context.RequestIds;
import io.qwenbridge.core.request.RequestViolation.Code;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts an untrusted {@link RawRequest} into a validated {@link UniversalRequest} (QB-001,
 * QB-005). All checks are cheap and run before any AI, retrieval or external call. Every violation
 * is collected so clients see all problems at once. Values are never echoed back.
 */
public final class UniversalRequestParser {

  private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9._-]*$");

  /**
   * Context keys whose names suggest credentials. Context metadata is logged and traced, so such
   * keys are refused instead of being silently carried along.
   */
  private static final Set<String> SENSITIVE_KEY_FRAGMENTS =
      Set.of(
          "password",
          "passwd",
          "secret",
          "token",
          "apikey",
          "authorization",
          "credential",
          "cookie",
          "privatekey",
          "bearer");

  private final RequestLimits limits;

  public UniversalRequestParser(RequestLimits limits) {
    this.limits = Objects.requireNonNull(limits, "limits must not be null");
  }

  public Result parse(RawRequest raw) {
    if (raw == null) {
      return new Result.Invalid(
          List.of(new RequestViolation("body", Code.REQUIRED, "must be present")));
    }
    List<RequestViolation> violations = new ArrayList<>();

    int version =
        raw.contractVersion() == null ? UniversalRequest.CONTRACT_VERSION : raw.contractVersion();
    if (version != UniversalRequest.CONTRACT_VERSION) {
      violations.add(
          new RequestViolation(
              "contractVersion",
              Code.UNSUPPORTED_VERSION,
              "must be " + UniversalRequest.CONTRACT_VERSION));
    }

    Optional<String> requestId = Optional.empty();
    if (raw.requestId() != null && !raw.requestId().isBlank()) {
      String trimmed = raw.requestId().trim();
      if (RequestIds.isValid(trimmed)) {
        requestId = Optional.of(trimmed);
      } else {
        violations.add(
            new RequestViolation(
                "requestId",
                Code.INVALID_FORMAT,
                "must be 1-" + RequestIds.MAX_LENGTH + " characters of [A-Za-z0-9._:-]"));
      }
    }

    validateQuery(raw.query(), violations);

    Optional<LanguageTag> declared =
        languageTag("declaredLanguage", raw.declaredLanguage(), violations);
    Optional<LanguageTag> response =
        languageTag("responseLanguage", raw.responseLanguage(), violations);
    Optional<LanguageTag> locale = languageTag("locale", raw.locale(), violations);

    Map<String, String> context = validateContext(raw.context(), violations);
    List<String> domainHints = validateDomainHints(raw.domainHints(), violations);

    if (!violations.isEmpty()) {
      return new Result.Invalid(List.copyOf(violations));
    }
    return new Result.Valid(
        new UniversalRequest(
            version,
            requestId,
            raw.query(),
            new LanguagePreferences(declared, response, locale),
            context,
            domainHints));
  }

  /**
   * A query is blank when it has no visible content: only whitespace and invisible format
   * characters (e.g. a lone zero-width non-joiner). {@link String#isBlank()} alone would accept
   * such input.
   */
  public static boolean isEffectivelyBlank(String text) {
    for (int i = 0; i < text.length(); ) {
      int codePoint = text.codePointAt(i);
      boolean invisible =
          Character.isWhitespace(codePoint)
              || Character.isSpaceChar(codePoint)
              || Character.getType(codePoint) == Character.FORMAT
              || Character.getType(codePoint) == Character.CONTROL;
      if (!invisible) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  private void validateQuery(String query, List<RequestViolation> violations) {
    if (query == null) {
      violations.add(new RequestViolation("query", Code.REQUIRED, "must be present"));
      return;
    }
    if (isEffectivelyBlank(query)) {
      violations.add(new RequestViolation("query", Code.BLANK, "must not be blank"));
      return;
    }
    if (query.codePointCount(0, query.length()) > limits.maxQueryCodePoints()) {
      violations.add(
          new RequestViolation(
              "query",
              Code.TOO_LONG,
              "must not exceed " + limits.maxQueryCodePoints() + " characters"));
    }
  }

  private static Optional<LanguageTag> languageTag(
      String field, String raw, List<RequestViolation> violations) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Optional<LanguageTag> tag = LanguageTag.parse(raw);
    if (tag.isEmpty()) {
      violations.add(
          new RequestViolation(
              field, Code.INVALID_LANGUAGE_TAG, "must be a supported BCP 47 language tag"));
    }
    return tag;
  }

  private Map<String, String> validateContext(
      Map<String, String> raw, List<RequestViolation> violations) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    if (raw.size() > limits.maxContextEntries()) {
      violations.add(
          new RequestViolation(
              "context",
              Code.TOO_MANY,
              "must not have more than " + limits.maxContextEntries() + " entries"));
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    // Sorted iteration keeps violation order deterministic regardless of the input map type.
    for (Map.Entry<String, String> entry : new java.util.TreeMap<>(raw).entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (!isIdentifier(key, limits.maxContextKeyLength())) {
        violations.add(
            new RequestViolation(
                "context",
                Code.INVALID_FORMAT,
                "keys must be lowercase identifiers of at most "
                    + limits.maxContextKeyLength()
                    + " characters"));
        continue;
      }
      if (isSensitiveKey(key)) {
        violations.add(
            new RequestViolation(
                "context", Code.SENSITIVE_CONTEXT_KEY, "must not carry credentials or secrets"));
        continue;
      }
      if (value == null) {
        violations.add(new RequestViolation("context", Code.REQUIRED, "values must be present"));
        continue;
      }
      if (value.codePointCount(0, value.length()) > limits.maxContextValueCodePoints()) {
        violations.add(
            new RequestViolation(
                "context",
                Code.TOO_LONG,
                "values must not exceed " + limits.maxContextValueCodePoints() + " characters"));
        continue;
      }
      result.put(key, value);
    }
    return result;
  }

  private List<String> validateDomainHints(List<String> raw, List<RequestViolation> violations) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    if (raw.size() > limits.maxDomainHints()) {
      violations.add(
          new RequestViolation(
              "domainHints",
              Code.TOO_MANY,
              "must not have more than " + limits.maxDomainHints() + " entries"));
      return List.of();
    }
    Set<String> hints = new LinkedHashSet<>();
    for (String hint : raw) {
      if (!isIdentifier(hint, limits.maxDomainHintLength())) {
        violations.add(
            new RequestViolation(
                "domainHints",
                Code.INVALID_FORMAT,
                "entries must be lowercase identifiers of at most "
                    + limits.maxDomainHintLength()
                    + " characters"));
        continue;
      }
      hints.add(hint);
    }
    return List.copyOf(hints);
  }

  private static boolean isIdentifier(String value, int maxLength) {
    return value != null
        && !value.isEmpty()
        && value.length() <= maxLength
        && IDENTIFIER.matcher(value).matches();
  }

  static boolean isSensitiveKey(String key) {
    String compact = key.toLowerCase(Locale.ROOT).replaceAll("[._-]", "");
    return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(compact::contains);
  }

  /** Outcome of parsing: either a valid request or the complete list of violations. */
  public sealed interface Result {
    record Valid(UniversalRequest request) implements Result {}

    record Invalid(List<RequestViolation> violations) implements Result {
      public Invalid {
        violations = List.copyOf(violations);
        if (violations.isEmpty()) {
          throw new IllegalArgumentException("Invalid result needs at least one violation");
        }
      }
    }
  }
}
