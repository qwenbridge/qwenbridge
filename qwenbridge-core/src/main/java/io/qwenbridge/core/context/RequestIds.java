package io.qwenbridge.core.context;

import java.util.regex.Pattern;

/**
 * The single rule for client-supplied identifiers (request ID, correlation ID) used by REST, SSE
 * and the SDKs (QB-006). Identifiers end up in headers, logs and SSE lookups, so only a safe ASCII
 * subset is accepted.
 */
public final class RequestIds {

  public static final int MAX_LENGTH = 128;

  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]*$");

  private RequestIds() {}

  public static boolean isValid(String id) {
    return id != null && !id.isEmpty() && id.length() <= MAX_LENGTH && SAFE.matcher(id).matches();
  }
}
