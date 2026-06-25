package io.qwenbridge.ai.api;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    private final AIService aiService;

    public AIChatController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public AIChatResponse chat(@Valid @RequestBody AIChatRequest request) {
        ChatResponse response = aiService.chat(new ChatRequest(request.prompt()));
        return new AIChatResponse(response.content());
    }
}
