package io.qwenbridge.operations.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceContextFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String TRACEPARENT_HEADER = "traceparent";
  public static final String MDC_TRACE_ID = "traceId";

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    TraceContext traceContext = resolve(request.getHeader(TRACEPARENT_HEADER));
    MDC.put(MDC_TRACE_ID, traceContext.traceId());
    response.setHeader(TRACE_ID_HEADER, traceContext.traceId());
    response.setHeader(TRACEPARENT_HEADER, traceContext.traceparent());
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TRACE_ID);
    }
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
