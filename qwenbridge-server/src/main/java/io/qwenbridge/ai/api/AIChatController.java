package io.qwenbridge.ai.api;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/ai")
@Tag(name = "AI", description = "AI provider bridge APIs")
public class AIChatController {

  private final AIService aiService;

  public AIChatController(AIService aiService) {
    this.aiService = aiService;
  }

  @Operation(
      summary = "Chat with the configured AI provider",
      description =
          """
          Sends a prompt to the configured QwenBridge AI provider.

          In the default local profile, QwenBridge routes the request to Ollama.
          Future V5 provider routing will allow multiple providers and model selection.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "AI chat completed successfully",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AIChatResponse.class),
              examples =
                  @ExampleObject(
                      name = "AI chat response",
                      value =
                          """
{
  "content": "QwenBridge is an AI-native search and provider bridge platform."
}
""")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid AI chat request",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Validation error",
                      value =
                          """
                          {
                            "timestamp": "2026-06-29T13:20:00Z",
                            "status": 400,
                            "error": "Bad Request",
                            "code": "VALIDATION_ERROR",
                            "message": "prompt prompt must not be blank",
                            "path": "/api/v1/ai/chat",
                            "requestId": "8b0f9d1c-6e71-4b99-ae2c-1ddbf19a97c2"
                          }
                          """)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class)))
  @POST
  @Path("/chat")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public AIChatResponse chat(@Valid AIChatRequest request) {
    ChatResponse response = aiService.chat(new ChatRequest(request.prompt()));
    return new AIChatResponse(response.content());
  }
}
