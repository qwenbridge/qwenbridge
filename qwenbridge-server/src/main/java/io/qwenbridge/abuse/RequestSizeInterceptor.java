package io.qwenbridge.abuse;

import io.qwenbridge.api.filter.RequestEnvelopeFilter;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bounds unknown-length/chunked JSON bodies before deserialization or AI execution, using the route
 * limit chosen by {@link RequestEnvelopeFilter}.
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class RequestSizeInterceptor implements ReaderInterceptor {
  private final AbuseProtectionProperties properties;

  public RequestSizeInterceptor(AbuseProtectionProperties properties) {
    this.properties = properties;
  }

  @Override
  public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
    Object routeLimit = context.getProperty(RequestEnvelopeFilter.BODY_LIMIT);
    int limit;
    if (routeLimit instanceof Integer value) {
      limit = value; // Always enforced on /api/ routes (QB-057).
    } else if (properties.enabled()) {
      limit = properties.requestSizeLimitBytes();
    } else {
      return context.proceed(); // Outside /api/: the transport ceiling applies.
    }
    byte[] body = context.getInputStream().readNBytes(limit + 1);
    if (body.length > limit) {
      throw new WebApplicationException("Request body exceeds configured size limit", 413);
    }
    context.setInputStream(new ByteArrayInputStream(body));
    Object entity = context.proceed();
    if (entity == null && !new String(body, StandardCharsets.UTF_8).isBlank()) {
      // A JSON "null" document is never a valid request body; reject it before any handler can
      // dereference it (found by the seeded HTTP fuzz test, QB-063).
      throw new WebApplicationException("Request body must be a JSON value", 400);
    }
    return entity;
  }
}
