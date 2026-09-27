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

  @Override
  public void filter(ContainerRequestContext requestContext) {
    TraceContext traceContext = resolve(requestContext.getHeaderString(TRACEPARENT_HEADER));

    MDC.put(MDC_TRACE_ID, traceContext.traceId());
    requestContext.setProperty(TRACE_CONTEXT_PROPERTY, traceContext);
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object stored = requestContext.getProperty(TRACE_CONTEXT_PROPERTY);

    if (stored instanceof TraceContext traceContext) {
      responseContext.getHeaders().putSingle(TRACE_ID_HEADER, traceContext.traceId());
      responseContext.getHeaders().putSingle(TRACEPARENT_HEADER, traceContext.traceparent());
    }

    MDC.remove(MDC_TRACE_ID);
  }

  private TraceContext resolve(String header) {
    if (header != null && header.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
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
