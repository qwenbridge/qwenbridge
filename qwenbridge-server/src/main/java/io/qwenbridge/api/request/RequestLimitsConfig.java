package io.qwenbridge.api.request;

import io.qwenbridge.core.request.RequestLimits;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configurable structural request limits (QB-005), {@code qwenbridge.request.limits.*}. */
@ConfigMapping(prefix = "qwenbridge.request.limits")
public interface RequestLimitsConfig {

  @WithDefault("2048")
  int maxQueryCodePoints();

  @WithDefault("16")
  int maxContextEntries();

  @WithDefault("64")
  int maxContextKeyLength();

  @WithDefault("256")
  int maxContextValueCodePoints();

  @WithDefault("8")
  int maxDomainHints();

  @WithDefault("64")
  int maxDomainHintLength();

  /** Maximum JSON object/array nesting accepted by the HTTP JSON parser. */
  @WithDefault("64")
  int maxJsonNestingDepth();

  /**
   * Largest request body accepted on {@code /api/} routes (QB-057, QB-058). Applies whether or not
   * abuse protection is enabled; defaults to the former abuse-protection limit.
   */
  @WithDefault("${qwenbridge.abuse.request-size-limit-bytes:65536}")
  int maxBodyBytes();

  /** Largest body on the upload routes listed in {@link #uploadPaths()} (signed packs). */
  @WithDefault("4194304")
  int maxUploadBodyBytes();

  /** Routes that accept uploads up to {@link #maxUploadBodyBytes()}; exact paths. */
  @WithDefault("/api/v2/platform/packs,/api/v2/platform/marketplace")
  java.util.List<String> uploadPaths();

  /** Longest JSON string value the parser accepts (QB-058). */
  @WithDefault("4194304")
  int maxJsonStringLength();

  /** Longest JSON number literal the parser accepts (QB-058). */
  @WithDefault("64")
  int maxJsonNumberLength();

  /** Longest JSON property name the parser accepts (QB-058). */
  @WithDefault("256")
  int maxJsonNameLength();

  default RequestLimits toLimits() {
    return new RequestLimits(
        maxQueryCodePoints(),
        maxContextEntries(),
        maxContextKeyLength(),
        maxContextValueCodePoints(),
        maxDomainHints(),
        maxDomainHintLength(),
        maxJsonNestingDepth());
  }
}
