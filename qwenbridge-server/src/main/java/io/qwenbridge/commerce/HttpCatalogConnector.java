package io.qwenbridge.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.catalog.connector.CatalogConnector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The built-in {@code http-json} catalog change feed; endpoints are trusted operator configuration.
 * Field meaning belongs to the Commerce pack's catalog mappings (QB-093), not to this transport.
 * Other sources are optional connector modules (QB-204).
 */
public final class HttpCatalogConnector implements CatalogConnector {
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final String token;

  public HttpCatalogConnector(URI endpoint, String token, ObjectMapper mapper) {
    if (endpoint.getScheme() == null || !endpoint.getScheme().matches("https?"))
      throw new IllegalArgumentException("Invalid connector URL");
    this.endpoint = endpoint;
    this.token = token == null ? "" : token;
    this.mapper = mapper.copy();
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public Page fetch(String cursor) {
    RuntimeException failure = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return fetchOnce(cursor);
      } catch (Retryable ex) {
        failure = ex;
        if (attempt < 2)
          try {
            Thread.sleep(100L * (attempt + 1));
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog retry interrupted", interrupted);
          }
      }
    }
    throw failure;
  }

  private static final class Retryable extends IllegalStateException {
    Retryable(String message) {
      super(message);
    }

    Retryable(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private Page fetchOnce(String cursor) {
    try {
      String url =
          endpoint.toString()
              + (endpoint.getQuery() == null ? "?" : "&")
              + "cursor="
              + URLEncoder.encode(cursor == null ? "" : cursor, StandardCharsets.UTF_8);
      var request = HttpRequest.newBuilder(URI.create(url)).GET();
      if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
      var response =
          client.send(
              request.timeout(Duration.ofSeconds(20)).build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429 || response.statusCode() >= 500)
        throw new Retryable("Transient catalog HTTP " + response.statusCode());
      if (response.statusCode() != 200 || response.body().length() > 4_194_304)
        throw new IllegalStateException("Catalog HTTP " + response.statusCode());
      return mapper.treeToValue(mapper.readTree(response.body()), Page.class);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Catalog fetch interrupted", ex);
    } catch (java.io.IOException ex) {
      throw new Retryable("Catalog fetch failed", ex);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
