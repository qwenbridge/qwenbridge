package io.qwenbridge.api;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.exception.ApiError;
import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.pipeline.SearchPipeline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "AI-native search analysis APIs")
public class SearchAnalyzeController {

    private final SearchPipeline searchPipeline;

    @Operation(
            summary = "Analyze a search query",
            description = """
                    Runs the QwenBridge AI search pipeline for a query.

                    The pipeline performs language detection, input normalization,
                    threat detection, AI analysis, intent detection, rewrite,
                    semantic validation, policy validation, decision planning,
                    execution, confidence scoring, and cache tracing.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Search query analyzed successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SearchAnalyzeResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid search analyze request",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Validation error",
                            value = """
                                    {
                                      "timestamp": "2026-06-29T13:20:00Z",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "code": "VALIDATION_ERROR",
                                      "message": "query query must not be blank",
                                      "path": "/api/v1/search/analyze",
                                      "requestId": "8b0f9d1c-6e71-4b99-ae2c-1ddbf19a97c2"
                                    }
                                    """
                    )
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
    @PostMapping("/analyze")
    public SearchAnalyzeResponse analyze(
            @RequestHeader(
                    value = ApiHeaders.REQUEST_ID,
                    required = false
            ) String headerRequestId,
            @Valid @RequestBody SearchAnalyzeRequest request
    ) {
        String requestId = resolveRequestId(
                headerRequestId,
                request.requestId()
        );

        SearchAnalyzeRequest effectiveRequest =
                new SearchAnalyzeRequest(
                        requestId,
                        request.query()
                );

        return searchPipeline.analyze(effectiveRequest);
    }

    private String resolveRequestId(
            String headerRequestId,
            String bodyRequestId
    ) {
        if (headerRequestId != null && !headerRequestId.isBlank()) {
            return headerRequestId.trim();
        }

        if (bodyRequestId != null && !bodyRequestId.isBlank()) {
            return bodyRequestId.trim();
        }

        return null;
    }
}