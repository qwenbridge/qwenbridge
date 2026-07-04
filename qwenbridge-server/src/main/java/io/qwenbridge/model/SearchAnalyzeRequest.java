package io.qwenbridge.model;

import jakarta.validation.constraints.NotBlank;

public record SearchAnalyzeRequest(
        String requestId,

        @NotBlank(message = "query must not be blank")
        String query
) {}
