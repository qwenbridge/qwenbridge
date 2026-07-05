package io.qwenbridge.operations.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HttpMetricsFilter extends OncePerRequestFilter {

  private final OperationsMetrics metrics;

  public HttpMetricsFilter(OperationsMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      metrics.recordHttpRequest(
          request.getMethod(),
          pathTemplate(request.getRequestURI()),
          response.getStatus(),
          Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private String pathTemplate(String path) {
    if (path == null || path.isBlank()) {
      return "unknown";
    }
    return path.replaceAll("/[0-9a-fA-F-]{16,}", "/{id}");
  }
}
