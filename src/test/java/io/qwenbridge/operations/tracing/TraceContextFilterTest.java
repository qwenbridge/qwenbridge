package io.qwenbridge.operations.tracing;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceContextFilterTest {

    private final TraceContextFilter filter = new TraceContextFilter();

    @Test
    void shouldPropagateValidW3cTraceparent() throws Exception {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(TraceContextFilter.TRACEPARENT_HEADER, traceparent);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, assertingChain("4bf92f3577b34da6a3ce929d0e0e4736"));

        assertEquals(traceparent, response.getHeader(TraceContextFilter.TRACEPARENT_HEADER));
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736",
                response.getHeader(TraceContextFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceContextFilter.MDC_TRACE_ID));
    }

    @Test
    void shouldGenerateValidTraceContextWhenHeaderIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(TraceContextFilter.TRACEPARENT_HEADER, "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, assertingChain(null));

        String traceId = response.getHeader(TraceContextFilter.TRACE_ID_HEADER);
        String traceparent = response.getHeader(TraceContextFilter.TRACEPARENT_HEADER);

        assertTrue(traceId.matches("[0-9a-f]{32}"));
        assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        assertEquals(traceId, traceparent.substring(3, 35));
        assertNull(MDC.get(TraceContextFilter.MDC_TRACE_ID));
    }

    private FilterChain assertingChain(String expectedTraceId) {
        return (request, response) -> {
            if (expectedTraceId != null) {
                assertEquals(expectedTraceId, MDC.get(TraceContextFilter.MDC_TRACE_ID));
            } else {
                assertTrue(MDC.get(TraceContextFilter.MDC_TRACE_ID).matches("[0-9a-f]{32}"));
            }
        };
    }
}
