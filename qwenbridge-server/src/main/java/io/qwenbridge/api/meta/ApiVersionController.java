package io.qwenbridge.api.meta;

import io.qwenbridge.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/v1/version")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Metadata", description = "QwenBridge metadata APIs")
public class ApiVersionController {

  private final String applicationName;
  private final String applicationVersion;

  public ApiVersionController(
      @ConfigProperty(name = "quarkus.application.name", defaultValue = "qwenbridge")
          String applicationName,
      @ConfigProperty(name = "qwenbridge.version", defaultValue = "0.1.0-SNAPSHOT")
          String applicationVersion) {
    this.applicationName = applicationName;
    this.applicationVersion = applicationVersion;
  }

  @Operation(
      summary = "Get version information",
      description = "Returns QwenBridge application, API, and runtime version information.")
  @ApiResponse(
      responseCode = "200",
      description = "Version information returned successfully",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiVersionResponse.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class)))
  @GET
  public ApiVersionResponse version() {
    return ApiVersionResponse.builder()
        .name(applicationName)
        .version(applicationVersion)
        .apiVersion("v1")
        .javaVersion(Runtime.version().toString())
        .build();
  }
}
