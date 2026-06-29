package io.qwenbridge.api.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class ApiHealthController {

    private final String applicationName;

    public ApiHealthController(
            @Value("${spring.application.name:qwenbridge}") String applicationName
    ) {
        this.applicationName = applicationName;
    }

    @Operation(summary = "Get public QwenBridge health status")
    @GetMapping
    public ApiHealthResponse health() {
        return new ApiHealthResponse(
                "UP",
                applicationName,
                "v1"
        );
    }
}
