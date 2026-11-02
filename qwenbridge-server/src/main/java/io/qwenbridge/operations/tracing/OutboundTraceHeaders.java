package io.qwenbridge.operations.tracing;

import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.core.context.RequestScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Correlation headers for outbound REST client calls made inside a request (QB-184): the request ID
 * ({@code X-Request-ID}, and {@code X-Opaque-Id} which OpenSearch records) and a child {@code
 * traceparent}. Nothing from the caller's own headers is forwarded.
 */
@ApplicationScoped
public final class OutboundTraceHeaders implements ClientHeadersFactory {

  public static void add(MultivaluedMap<String, String> headers) {
    RequestScope.current()
        .ifPresent(
            scope -> {
              headers.putSingle(ApiHeaders.REQUEST_ID, scope.requestId());
              headers.putSingle("X-Opaque-Id", scope.requestId());
              scope
                  .outboundTraceparent()
                  .ifPresent(t -> headers.putSingle(TraceContextFilter.TRACEPARENT_HEADER, t));
            });
  }

  @Override
  public MultivaluedMap<String, String> update(
      MultivaluedMap<String, String> incoming, MultivaluedMap<String, String> outgoing) {
    var headers = new MultivaluedHashMap<String, String>();
    add(headers);
    return headers;
  }
}
