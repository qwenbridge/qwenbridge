package io.qwenbridge.sdk.retry;

import io.qwenbridge.sdk.exception.QwenBridgeApiError;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryClassifierTest {

    @Test
    void shouldRetryTransportFailures() {
        QwenBridgeTransportException exception =
                new QwenBridgeTransportException(
                        "connection refused",
                        new RuntimeException("connection refused")
                );

        assertTrue(RetryClassifier.isRetryable(exception));
    }

    @Test
    void shouldRetryRateLimitedApiError() {
        assertTrue(RetryClassifier.isRetryable(apiException(429)));
    }

    @Test
    void shouldRetryBadGatewayApiError() {
        assertTrue(RetryClassifier.isRetryable(apiException(502)));
    }

    @Test
    void shouldRetryServiceUnavailableApiError() {
        assertTrue(RetryClassifier.isRetryable(apiException(503)));
    }

    @Test
    void shouldRetryGatewayTimeoutApiError() {
        assertTrue(RetryClassifier.isRetryable(apiException(504)));
    }

    @Test
    void shouldNotRetryValidationError() {
        assertFalse(RetryClassifier.isRetryable(apiException(400)));
    }

    @Test
    void shouldNotRetryUnauthorizedError() {
        assertFalse(RetryClassifier.isRetryable(apiException(401)));
    }

    @Test
    void shouldNotRetryForbiddenError() {
        assertFalse(RetryClassifier.isRetryable(apiException(403)));
    }

    @Test
    void shouldNotRetryNotFoundError() {
        assertFalse(RetryClassifier.isRetryable(apiException(404)));
    }

    @Test
    void shouldNotRetryUnexpectedException() {
        assertFalse(RetryClassifier.isRetryable(
                new IllegalStateException("unexpected failure")
        ));
    }

    private QwenBridgeApiException apiException(int status) {
        return new QwenBridgeApiException(new QwenBridgeApiError(
                Instant.parse("2026-07-04T14:00:00Z"),
                status,
                "HTTP Error",
                "TEST_ERROR",
                "test failure",
                "/api/v1/search/analyze",
                "req-test"
        ));
    }
}
