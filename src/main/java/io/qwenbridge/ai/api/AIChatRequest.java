package io.qwenbridge.ai.api;

import jakarta.validation.constraints.NotBlank;

public record AIChatRequest(
        @NotBlank(message = "prompt must not be blank")
        String prompt
) {
}
