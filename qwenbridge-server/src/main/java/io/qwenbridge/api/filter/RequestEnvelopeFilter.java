package io.qwenbridge.api.filter;

import io.qwenbridge.api.request.RequestLimitsConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.util.Locale;
import java.util.Set;

/**
 * First stage of the ordered request validation pipeline (QB-057, QB-058), always on and
 * independent of abuse protection. Every request to {@code /api/} passes these stages in order, and
 * the cheap ones run before anything expensive:
 *
 * <ol>
 *   <li>transport: header size and a hard body ceiling (Vert.x, {@code quarkus.http.limits});
 *   <li>request identity: a safe request ID ({@link RequestIdFilter});
 *   <li><b>envelope (this filter):</b> a body needs a JSON content type, and its declared size must
 *       fit the route's limit ({@code qwenbridge.request.limits.max-body-bytes}, larger only on the
 *       configured upload routes); chunked bodies are bounded while being read ({@code
 *       RequestSizeInterceptor});
 *   <li>rate limits ({@code AbuseProtectionFilter}, when enabled) and authentication;
 *   <li>structure: JSON nesting, string, number and name limits in the parser;
 *   <li>fields: the universal request contract ({@code UniversalRequestParser});
 *   <li>security: deterministic threat rules, then model safety, before any search or generation.
 * </ol>
 */
@Provider
@Priority(4)
public class RequestEnvelopeFilter implements ContainerRequestFilter {

  /** Request property carrying the body limit for this route, read by the size interceptor. */
  public static final String BODY_LIMIT = "qwenbridge.bodyLimitBytes";

  private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

  /** Resolved per request: providers are created before config mappings are available. */
  @Inject Instance<RequestLimitsConfig> limits;

  @Override
  public void filter(ContainerRequestContext request) {
    String path = path(request);
    if (!path.startsWith("/api/")) {
      return;
    }
    RequestLimitsConfig config = limits.get();
    int limit =
        config.uploadPaths().contains(path) ? config.maxUploadBodyBytes() : config.maxBodyBytes();
    request.setProperty(BODY_LIMIT, limit);
    if (!BODY_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT)) || !hasBody(request)) {
      return;
    }
    if (!json(request.getMediaType())) {
      throw new WebApplicationException("Unsupported content type", 415);
    }
    if (request.getLength() > limit) {
      throw new WebApplicationException("Request body exceeds configured size limit", 413);
    }
  }

  static boolean json(MediaType type) {
    if (type == null || !"application".equalsIgnoreCase(type.getType())) {
      return false;
    }
    String subtype = type.getSubtype().toLowerCase(Locale.ROOT);
    return subtype.equals("json") || subtype.endsWith("+json");
  }

  private static boolean hasBody(ContainerRequestContext request) {
    if (request.getLength() > 0) {
      return true;
    }
    String encoding = request.getHeaderString("Transfer-Encoding");
    return encoding != null && encoding.toLowerCase(Locale.ROOT).contains("chunked")
        || request.getLength() < 0 && request.getHeaderString(HttpHeaders.CONTENT_TYPE) != null;
  }

  private static String path(ContainerRequestContext request) {
    String path = request.getUriInfo().getPath();
    return path.startsWith("/") ? path : "/" + path;
  }
}
