package io.qwenbridge.api.requests;

import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Request control and API metadata for the v2 API (QB-179, QB-182). */
@Path("/api/v2")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class RequestsResource {

  /** The versioned contracts this server implements (QB-179). */
  public record Version(
      String name,
      String version,
      List<String> apiVersions,
      Map<String, String> contracts,
      Map<String, Object> limits) {
    public Version {
      apiVersions = List.copyOf(apiVersions);
      contracts = Map.copyOf(contracts);
      limits = Map.copyOf(limits);
    }
  }

  public record Cancelled(String requestId, boolean cancelled) {}

  @Inject ApiRequests requests;
  @Context ContainerRequestContext context;

  @ConfigProperty(name = "quarkus.application.name", defaultValue = "qwenbridge")
  String name;

  @ConfigProperty(name = "qwenbridge.version")
  String version;

  @GET
  @Path("/version")
  public Version version() {
    ApiIdentity.caller(context);
    return new Version(
        name,
        version,
        List.of("v1", "v2"),
        Map.of(
            "error", "api-error/2",
            "sse", "progress-event/2",
            "commerceSearch", "commerce-search/1.0",
            "queryIR", "1.0",
            "connectorConfig", "connector-config/1"),
        Map.of(
            "maxPageSize",
            requests.maxPageSize(),
            "requestTimeoutMs",
            requests.maxTimeout().toMillis()));
  }

  /** Cancels the caller's own in-flight request; unknown and foreign requests are 404. */
  @DELETE
  @Path("/requests/{requestId}")
  public Cancelled cancel(@PathParam("requestId") String requestId) {
    var caller = ApiIdentity.caller(context);
    if (!io.qwenbridge.core.context.RequestIds.isValid(requestId)
        || !requests.cancel(caller, requestId))
      throw new NotFoundException("No in-flight request with that ID");
    return new Cancelled(requestId, true);
  }
}
