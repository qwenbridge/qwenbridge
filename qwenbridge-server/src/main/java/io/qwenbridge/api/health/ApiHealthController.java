package io.qwenbridge.api.health;

import io.qwenbridge.exception.ApiError;
import io.qwenbridge.operations.health.OperationalHealthService;
import io.qwenbridge.operations.health.OperationalStatus;
import io.qwenbridge.operations.health.ReadinessHealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/v1/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Public QwenBridge health APIs")
public class ApiHealthController {

  private final String applicationName;
  private final OperationalHealthService operationalHealthService;

  public ApiHealthController(
      @ConfigProperty(name = "quarkus.application.name", defaultValue = "qwenbridge")
          String applicationName,
      OperationalHealthService operationalHealthService) {
    this.applicationName = applicationName;
    this.operationalHealthService = operationalHealthService;
  }

  @Operation(
      summary = "Get public health status",
      description = "Returns the public health status of QwenBridge.")
  @ApiResponse(
      responseCode = "200",
      description = "QwenBridge is healthy",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiHealthResponse.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class)))
  @GET
  public ApiHealthResponse health() {
    return liveness();
  }

  @GET
  @Path("/live")
  public ApiHealthResponse liveness() {
    return ApiHealthResponse.builder()
        .status("UP")
        .service(applicationName)
        .apiVersion("v1")
        .build();
  }

  @GET
  @Path("/ready")
  public Response readiness() {
    ReadinessHealthResponse response = operationalHealthService.readiness();

    Response.Status httpStatus =
        response.status() == OperationalStatus.DOWN
            ? Response.Status.SERVICE_UNAVAILABLE
            : Response.Status.OK;

    return Response.status(httpStatus).entity(response).build();
  }
}
