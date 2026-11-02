package io.qwenbridge.core.spelling;

import java.util.List;
import java.util.Objects;

/**
 * The status of one configured language identifier (QB-291 A4). A valid identifier does not imply
 * that a correction provider exists for it; another language is never substituted.
 *
 * @param configured the identifier as configured
 * @param providers the providers that can correct it (empty unless {@code SUPPORTED})
 */
public record LanguageSupport(String configured, Status status, List<String> providers) {

  public enum Status {
    /** Valid identifier with at least one installed provider. */
    SUPPORTED,
    /** Valid identifier, but no installed provider handles it. */
    PROVIDER_UNAVAILABLE,
    /** Not a valid BCP 47 identifier. */
    INVALID_IDENTIFIER
  }

  public LanguageSupport {
    Objects.requireNonNull(configured, "configured must not be null");
    Objects.requireNonNull(status, "status must not be null");
    providers = List.copyOf(providers);
  }
}
