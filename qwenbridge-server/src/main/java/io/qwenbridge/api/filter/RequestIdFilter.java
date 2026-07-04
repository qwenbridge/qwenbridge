package io.qwenbridge.api.filter;

import io.qwenbridge.api.header.ApiHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        MDC.put(MDC_REQUEST_ID, requestId);
        request.setAttribute(ApiHeaders.REQUEST_ID, requestId);
        response.setHeader(ApiHeaders.REQUEST_ID, requestId);
        response.setHeader(ApiHeaders.QWENBRIDGE_VERSION, "0.1.0-SNAPSHOT");

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(ApiHeaders.REQUEST_ID);

        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return requestId.trim();
    }
}
