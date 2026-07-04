package io.qwenbridge.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.exception.QwenBridgeApiError;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public class QwenBridgeClient {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final QwenBridgeClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QwenBridgeClient(QwenBridgeClientConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build(), new ObjectMapper());
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

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), SearchAnalyzeResponse.class);
            }

            throw new QwenBridgeApiException(
                    objectMapper.readValue(response.body(), QwenBridgeApiError.class)
            );
        } catch (QwenBridgeApiException e) {
            throw e;
        } catch (IOException e) {
            throw new QwenBridgeTransportException("Failed to call QwenBridge API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QwenBridgeTransportException("QwenBridge API call was interrupted", e);
        }
    }

    private URI endpoint(String path) {
        return config.baseUrl().resolve(path);
    }
}
