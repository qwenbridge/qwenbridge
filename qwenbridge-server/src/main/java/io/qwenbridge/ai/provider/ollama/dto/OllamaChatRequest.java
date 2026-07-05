package io.qwenbridge.ai.provider.ollama.dto;

import java.util.List;

public record OllamaChatRequest(String model, List<Message> messages, boolean stream) {

  public record Message(String role, String content) {}
}
