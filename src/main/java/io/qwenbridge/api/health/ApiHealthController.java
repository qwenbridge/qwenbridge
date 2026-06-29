package io.qwenbridge.api.health;

import io.qwenbridge.exception.ApiError;
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

    public ApiHealthController(
            @Value("${spring.application.name:qwenbridge}") String applicationName
    ) {
        this.applicationName = applicationName;
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
        return new ApiHealthResponse(
                "UP",
                applicationName,
                "v1"
        );
    }
}
