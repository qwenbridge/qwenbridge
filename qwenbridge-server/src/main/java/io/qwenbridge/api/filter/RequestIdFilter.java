package io.qwenbridge.api.filter;

import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.core.context.CorrelationContext;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

/**
 * Resolves request and correlation identifiers for every request (QB-006). Client values are only
 * accepted when they match the shared safe-identifier rule; otherwise a fresh ID is generated and
 * the unsafe value is never echoed into headers, logs or error bodies.
 */
@Provider
@Priority(2)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String MDC_REQUEST_ID = "requestId";
  public static final String MDC_CORRELATION_ID = "correlationId";
  public static final String GENERATED_REQUEST_ID = "qwenbridge.generatedRequestId";
  public static final String CORRELATION_CONTEXT = "qwenbridge.correlationContext";

  private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);

  @ConfigProperty(name = "qwenbridge.version")
  String applicationVersion;

  @Context RoutingContext routingContext;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    CorrelationContext correlation =
        CorrelationContext.resolve(
            requestContext.getHeaderString(ApiHeaders.REQUEST_ID),
            requestContext.getHeaderString(ApiHeaders.CORRELATION_ID),
            () -> UUID.randomUUID().toString());
    if (correlation.requestIdReplaced()) {
      LOG.debugf("Replaced unsafe client request ID with %s", correlation.requestId());
    }

    String requestId = correlation.requestId();
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CORRELATION_ID, correlation.correlationId());
    requestContext.setProperty(ApiHeaders.REQUEST_ID, requestId);
    requestContext.setProperty(GENERATED_REQUEST_ID, correlation.requestIdGenerated());
    requestContext.setProperty(CORRELATION_CONTEXT, correlation);
    // Downstream readers (controllers, exception mappers) see only the resolved, safe value.
    requestContext.getHeaders().putSingle(ApiHeaders.REQUEST_ID, requestId);
    requestContext.getHeaders().putSingle(ApiHeaders.CORRELATION_ID, correlation.correlationId());
    // An SSE sink can commit before JAX-RS response filters are invoked.
    if (routingContext != null) {
      routingContext.response().putHeader(ApiHeaders.REQUEST_ID, requestId);
      routingContext.response().putHeader(ApiHeaders.CORRELATION_ID, correlation.correlationId());
      routingContext.response().putHeader(ApiHeaders.QWENBRIDGE_VERSION, applicationVersion);
    }
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object requestId = requestContext.getProperty(ApiHeaders.REQUEST_ID);
    Object correlation = requestContext.getProperty(CORRELATION_CONTEXT);

    // Each header once: the request filter may already have put it on the HTTP response.
    putOnce(responseContext, ApiHeaders.REQUEST_ID, requestId instanceof String value ? value : "");
    if (correlation instanceof CorrelationContext context) {
      putOnce(responseContext, ApiHeaders.CORRELATION_ID, context.correlationId());
    }
    putOnce(responseContext, ApiHeaders.QWENBRIDGE_VERSION, applicationVersion);
    MDC.remove(MDC_REQUEST_ID);
    MDC.remove(MDC_CORRELATION_ID);
  }

  private void putOnce(ContainerResponseContext response, String name, String value) {
    if (routingContext != null && routingContext.response().headers().contains(name)) {
      response.getHeaders().remove(name);
    } else {
      response.getHeaders().putSingle(name, value);
    }
  }
}
