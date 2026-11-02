package io.qwenbridge.execution.provider.opensearch.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

@ApplicationScoped
public final class OpenSearchAuthHeaders implements ClientHeadersFactory {
  private final String authorization;

  @jakarta.inject.Inject
  public OpenSearchAuthHeaders(
      @ConfigProperty(name = "qwenbridge.search.opensearch.username")
          java.util.Optional<String> username,
      @ConfigProperty(name = "qwenbridge.search.opensearch.password")
          java.util.Optional<String> password,
      @ConfigProperty(name = "qwenbridge.search.opensearch.bearer-token")
          java.util.Optional<String> bearer) {
    this(username.orElse(""), password.orElse(""), bearer.orElse(""));
  }

  public OpenSearchAuthHeaders(String username, String password, String bearer) {
    if ((!password.isBlank() && username.isBlank()) || (!bearer.isBlank() && !username.isBlank()))
      throw new IllegalArgumentException("Choose one OpenSearch authentication method");
    authorization =
        !bearer.isBlank()
            ? "Bearer " + bearer
            : username.isBlank()
                ? ""
                : "Basic "
                    + Base64.getEncoder()
                        .encodeToString(
                            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  public MultivaluedMap<String, String> update(
      MultivaluedMap<String, String> incoming, MultivaluedMap<String, String> outgoing) {
    // Only configured service credentials are forwarded, never the caller's Authorization header.
    var headers = new MultivaluedHashMap<String, String>();
    if (!authorization.isEmpty()) headers.putSingle("Authorization", authorization);
    io.qwenbridge.operations.tracing.OutboundTraceHeaders.add(headers);
    return headers;
  }
}
