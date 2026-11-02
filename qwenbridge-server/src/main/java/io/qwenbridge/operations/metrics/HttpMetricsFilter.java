package io.qwenbridge.operations.metrics;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.time.Duration;

@Provider
@Priority(1)
public class HttpMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String START_TIME_PROPERTY = "qwenbridge.metrics.startNanos";

  private final OperationsMetrics metrics;

  public HttpMetricsFilter(OperationsMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object started = requestContext.getProperty(START_TIME_PROPERTY);
    if (!(started instanceof Long startNanos)) {
      return;
    }

    metrics.recordHttpRequest(
        requestContext.getMethod(),
        pathTemplate("/" + requestContext.getUriInfo().getPath()),
        responseContext.getStatus(),
        Duration.ofNanos(System.nanoTime() - startNanos));
  }

  private String pathTemplate(String path) {
    if (path == null || path.isBlank()) {
      return "unknown";
    }
    return path.replaceAll("/[0-9a-fA-F-]{16,}", "/{id}");
  }
}
