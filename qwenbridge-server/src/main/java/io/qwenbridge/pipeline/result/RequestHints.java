package io.qwenbridge.pipeline.result;

import java.util.List;
import java.util.Map;

/**
 * Validated, domain-neutral request metadata made available to pipeline steps.
 *
 * @param domainHints domains the client asked for
 * @param context validated client context (e.g. {@code timezone})
 */
public record RequestHints(List<String> domainHints, Map<String, String> context) {

  public RequestHints {
    domainHints = domainHints == null ? List.of() : List.copyOf(domainHints);
    context = context == null ? Map.of() : Map.copyOf(context);
  }

  public RequestHints(List<String> domainHints) {
    this(domainHints, Map.of());
  }

  public static RequestHints none() {
    return new RequestHints(List.of(), Map.of());
  }
}
