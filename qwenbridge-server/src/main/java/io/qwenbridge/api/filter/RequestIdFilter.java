package io.qwenbridge.api.filter;

import io.qwenbridge.api.header.ApiHeaders;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.slf4j.MDC;

@Provider
@Priority(2)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String MDC_REQUEST_ID = "requestId";
  public static final String GENERATED_REQUEST_ID = "qwenbridge.generatedRequestId";

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String headerRequestId = requestContext.getHeaderString(ApiHeaders.REQUEST_ID);
    boolean generated = headerRequestId == null || headerRequestId.isBlank();
    String requestId = resolveRequestId(headerRequestId);

    MDC.put(MDC_REQUEST_ID, requestId);
    requestContext.setProperty(ApiHeaders.REQUEST_ID, requestId);
    requestContext.setProperty(GENERATED_REQUEST_ID, generated);
    requestContext.getHeaders().putSingle(ApiHeaders.REQUEST_ID, requestId);
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Object requestId = requestContext.getProperty(ApiHeaders.REQUEST_ID);

    responseContext
        .getHeaders()
        .putSingle(ApiHeaders.REQUEST_ID, requestId instanceof String value ? value : "");
    responseContext.getHeaders().putSingle(ApiHeaders.QWENBRIDGE_VERSION, "0.1.0-SNAPSHOT");

    MDC.remove(MDC_REQUEST_ID);
  }

  private String resolveRequestId(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return UUID.randomUUID().toString();
    }

    return headerValue.trim();
  }
}
