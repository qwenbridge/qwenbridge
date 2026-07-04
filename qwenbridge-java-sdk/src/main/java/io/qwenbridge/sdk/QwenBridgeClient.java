package io.qwenbridge.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.exception.QwenBridgeApiError;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.retry.ExponentialBackoff;
import io.qwenbridge.sdk.retry.RetryClassifier;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class QwenBridgeClient {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final QwenBridgeClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QwenBridgeClient(QwenBridgeClientConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    QwenBridgeClient(
            QwenBridgeClientConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public static QwenBridgeClient localDefault() {
        return new QwenBridgeClient(QwenBridgeClientConfig.localDefault());
    }

    public SearchAnalyzeResponse analyze(SearchAnalyzeRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= config.retryPolicy().maxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        buildAnalyzeRequest(request),
                        HttpResponse.BodyHandlers.ofString()
                );

                return handleAnalyzeResponse(response);
            } catch (QwenBridgeApiException | QwenBridgeTransportException e) {
                lastFailure = e;

                if (!shouldRetry(e, attempt)) {
                    throw e;
                }

                sleepBeforeRetry(attempt);
            } catch (IOException e) {
                QwenBridgeTransportException wrapped =
                        new QwenBridgeTransportException("Failed to call QwenBridge API", e);

                lastFailure = wrapped;

                if (!shouldRetry(wrapped, attempt)) {
                    throw wrapped;
                }

                sleepBeforeRetry(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new QwenBridgeTransportException("QwenBridge API call was interrupted", e);
            }
        }

        throw lastFailure;
    }

    public CompletableFuture<SearchAnalyzeResponse> analyzeAsync(SearchAnalyzeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return analyzeAsync(request, 1);
    }

    private CompletableFuture<SearchAnalyzeResponse> analyzeAsync(
            SearchAnalyzeRequest request,
            int attempt
    ) {
        return httpClient.sendAsync(
                        buildAnalyzeRequest(request),
                        HttpResponse.BodyHandlers.ofString()
                )
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrapCompletionThrowable(throwable);
                        return asyncFailure(
                                request,
                                attempt,
                                new QwenBridgeTransportException(
                                        "Failed to call QwenBridge API",
                                        cause
                                )
                        );
                    }

                    try {
                        return CompletableFuture.completedFuture(
                                handleAnalyzeResponse(response)
                        );
                    } catch (QwenBridgeApiException | QwenBridgeTransportException e) {
                        return asyncFailure(request, attempt, e);
                    }
                })
                .thenCompose(future -> future);
    }

    private HttpRequest buildAnalyzeRequest(SearchAnalyzeRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        try {
            String body = objectMapper.writeValueAsString(request);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpoint("/api/v1/search/analyze"))
                    .timeout(config.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (request.requestId() != null && !request.requestId().isBlank()) {
                builder.header(REQUEST_ID_HEADER, request.requestId().trim());
            }

            return builder.build();
        } catch (IOException e) {
            throw new QwenBridgeTransportException("Failed to serialize QwenBridge request", e);
        }
    }

    private SearchAnalyzeResponse handleAnalyzeResponse(HttpResponse<String> response) {
        try {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), SearchAnalyzeResponse.class);
            }

            throw new QwenBridgeApiException(
                    objectMapper.readValue(response.body(), QwenBridgeApiError.class)
            );
        } catch (QwenBridgeApiException e) {
            throw e;
        } catch (IOException e) {
            throw new QwenBridgeTransportException("Failed to parse QwenBridge response", e);
        }
    }

    private CompletableFuture<SearchAnalyzeResponse> asyncFailure(
            SearchAnalyzeRequest request,
            int attempt,
            RuntimeException failure
    ) {
        if (!shouldRetry(failure, attempt)) {
            return CompletableFuture.failedFuture(failure);
        }

        return CompletableFuture
                .supplyAsync(() -> {
                    sleepBeforeRetry(attempt);
                    return null;
                })
                .thenCompose(ignored -> analyzeAsync(request, attempt + 1));
    }

    private boolean shouldRetry(Throwable throwable, int attempt) {
        return attempt < config.retryPolicy().maxAttempts()
                && RetryClassifier.isRetryable(throwable);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(
                    ExponentialBackoff.delayForAttempt(
                            config.retryPolicy(),
                            attempt
                    ).toMillis()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QwenBridgeTransportException(
                    "QwenBridge retry sleep was interrupted",
                    e
            );
        }
    }

    private Throwable unwrapCompletionThrowable(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }

    private URI endpoint(String path) {
        return config.baseUrl().resolve(path);
    }
}
