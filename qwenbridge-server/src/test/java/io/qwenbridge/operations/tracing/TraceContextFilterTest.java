package io.qwenbridge.operations.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceContextFilterTest {

  private final TraceContextFilter filter = new TraceContextFilter();

  @Test
  void shouldPropagateValidW3cTraceparent() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    Map<String, Object> requestProperties = new HashMap<>();
    ContainerRequestContext request = request(traceparent, requestProperties);
    MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();

    filter.filter(request);

    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get(TraceContextFilter.MDC_TRACE_ID));

    filter.filter(request, response(responseHeaders));

    assertEquals(traceparent, responseHeaders.getFirst(TraceContextFilter.TRACEPARENT_HEADER));
    assertEquals(
        "4bf92f3577b34da6a3ce929d0e0e4736",
        responseHeaders.getFirst(TraceContextFilter.TRACE_ID_HEADER));
    assertNull(MDC.get(TraceContextFilter.MDC_TRACE_ID));
  }

  @Test
  void shouldGenerateValidTraceContextWhenHeaderIsMissingOrInvalid() {
    Map<String, Object> requestProperties = new HashMap<>();
    ContainerRequestContext request = request("invalid", requestProperties);
    MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();

    filter.filter(request);

    assertTrue(MDC.get(TraceContextFilter.MDC_TRACE_ID).matches("[0-9a-f]{32}"));

    filter.filter(request, response(responseHeaders));

    String traceId = (String) responseHeaders.getFirst(TraceContextFilter.TRACE_ID_HEADER);
    String traceparent = (String) responseHeaders.getFirst(TraceContextFilter.TRACEPARENT_HEADER);

    assertTrue(traceId.matches("[0-9a-f]{32}"));
    assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
    assertEquals(traceId, traceparent.substring(3, 35));
    assertNull(MDC.get(TraceContextFilter.MDC_TRACE_ID));
  }

  private ContainerRequestContext request(String traceparent, Map<String, Object> properties) {
    ContainerRequestContext request = mock(ContainerRequestContext.class);
    when(request.getHeaderString(TraceContextFilter.TRACEPARENT_HEADER)).thenReturn(traceparent);
    when(request.getProperty(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
    org.mockito.Mockito.doAnswer(
            invocation -> {
              properties.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(request)
        .setProperty(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    return request;
  }

  private ContainerResponseContext response(MultivaluedMap<String, Object> headers) {
    ContainerResponseContext response = mock(ContainerResponseContext.class);
    when(response.getHeaders()).thenReturn(headers);
    return response;
  }
}
