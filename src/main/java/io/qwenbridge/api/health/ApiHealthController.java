package io.qwenbridge.api.health;

import io.qwenbridge.exception.ApiError;
import io.qwenbridge.operations.health.OperationalHealthService;
import io.qwenbridge.operations.health.OperationalStatus;
import io.qwenbridge.operations.health.ReadinessHealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Public QwenBridge health APIs")
public class ApiHealthController {

    private final String applicationName;
    private final OperationalHealthService operationalHealthService;

    public ApiHealthController(
            @Value("${spring.application.name:qwenbridge}") String applicationName,
            OperationalHealthService operationalHealthService
    ) {
        this.applicationName = applicationName;
        this.operationalHealthService = operationalHealthService;
    }

    @Operation(
            summary = "Get public health status",
            description = "Returns the public health status of QwenBridge."
    )
    @ApiResponse(
            responseCode = "200",
            description = "QwenBridge is healthy",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiHealthResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class)
            )
    )
    @GetMapping
    public ApiHealthResponse health() {
        return liveness();
    }

    @GetMapping("/live")
    public ApiHealthResponse liveness() {
        return ApiHealthResponse.builder()
                .status("UP")
                .service(applicationName)
                .apiVersion("v1")
                .build();
    }

    @GetMapping("/ready")
    public ResponseEntity<ReadinessHealthResponse> readiness() {
        ReadinessHealthResponse response = operationalHealthService.readiness();

        HttpStatus httpStatus = response.status() == OperationalStatus.DOWN
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;

        return ResponseEntity.status(httpStatus).body(response);
    }
}
