package io.qwenbridge.ai.provider.ollama.dto;

public record OllamaStreamingChatResponse(
    String model, OllamaChatResponse.Message message, boolean done) {

  public String content() {
    return message == null || message.content() == null ? "" : message.content();
  }
}
