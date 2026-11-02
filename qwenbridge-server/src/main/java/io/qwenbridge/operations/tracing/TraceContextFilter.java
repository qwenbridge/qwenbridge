package io.qwenbridge.operations.tracing;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;

@Provider
@Priority(3)
public class TraceContextFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String TRACEPARENT_HEADER = "traceparent";
  public static final String MDC_TRACE_ID = "traceId";

  private static final String TRACE_CONTEXT_PROPERTY = "qwenbridge.traceContext";
  private static final SecureRandom RANDOM = new SecureRandom();

  @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext routingContext;

  /** The request's trace parent, as resolved by this filter (QB-184). */
  public static java.util.Optional<String> traceparent(ContainerRequestContext requestContext) {
    return requestContext.getProperty(TRACE_CONTEXT_PROPERTY) instanceof TraceContext trace
        ? java.util.Optional.of(trace.traceparent())
        : java.util.Optional.empty();
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    TraceContext traceContext = resolve(requestContext.getHeaderString(TRACEPARENT_HEADER));

    MDC.put(MDC_TRACE_ID, traceContext.traceId());
    requestContext.setProperty(TRACE_CONTEXT_PROPERTY, traceContext);
    // An SSE stream commits its headers before response filters run (QB-184).
    if (routingContext != null) {
      routingContext.response().putHeader(TRACE_ID_HEADER, traceContext.traceId());
      routingContext.response().putHeader(TRACEPARENT_HEADER, traceContext.traceparent());
    }
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object stored = requestContext.getProperty(TRACE_CONTEXT_PROPERTY);

    if (stored instanceof TraceContext traceContext) {
      // Set once: the request filter already put them on the HTTP response (QB-184).
      putOnce(responseContext, TRACE_ID_HEADER, traceContext.traceId());
      putOnce(responseContext, TRACEPARENT_HEADER, traceContext.traceparent());
    }

    MDC.remove(MDC_TRACE_ID);
  }

  private void putOnce(ContainerResponseContext response, String name, String value) {
    if (routingContext != null && routingContext.response().headers().contains(name)) {
      response.getHeaders().remove(name);
    } else {
      response.getHeaders().putSingle(name, value);
    }
  }

  private TraceContext resolve(String header) {
    // Only well-formed version-00 parents with non-zero IDs are continued (W3C Trace Context).
    if (io.qwenbridge.core.context.RequestScope.validTraceparent(header)) {
      return new TraceContext(header.substring(3, 35), header);
    }
    String traceId = randomHex(16);
    String spanId = randomHex(8);
    return new TraceContext(traceId, "00-" + traceId + "-" + spanId + "-01");
  }

  private String randomHex(int bytes) {
    byte[] value = new byte[bytes];
    RANDOM.nextBytes(value);
    return HexFormat.of().formatHex(value);
  }
}
