package io.qwenbridge.api.meta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/version")
@Tag(name = "Metadata")
public class ApiVersionController {

    private final String applicationName;
    private final String applicationVersion;

    public ApiVersionController(
            @Value("${spring.application.name:qwenbridge}") String applicationName,
            @Value("${qwenbridge.version:0.1.0-SNAPSHOT}") String applicationVersion
    ) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @Operation(summary = "Get QwenBridge version information")
    @GetMapping
    public ApiVersionResponse version() {
        return new ApiVersionResponse(
                applicationName,
                applicationVersion,
                "v1",
                Runtime.version().toString()
        );
    }
}
