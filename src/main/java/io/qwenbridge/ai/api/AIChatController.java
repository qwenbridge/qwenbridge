package io.qwenbridge.ai.api;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI")
public class AIChatController {

    private final AIService aiService;

    public AIChatController(AIService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "Chat with AI")
    @PostMapping("/chat")
    public AIChatResponse chat(@Valid @RequestBody AIChatRequest request) {
        ChatResponse response = aiService.chat(new ChatRequest(request.prompt()));
        return new AIChatResponse(response.content());
    }
}
