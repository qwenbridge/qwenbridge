package io.qwenbridge.ai.provider.ollama.dto;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonInclude(
    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
    String model,
    List<Message> messages,
    boolean stream,
    java.util.Map<String, Object> format,
    java.util.Map<String, Object> options) {
  public OllamaChatRequest(String model, List<Message> messages, boolean stream) {
    this(model, messages, stream, null, null);
  }

  public OllamaChatRequest {
    messages = messages == null ? List.of() : List.copyOf(messages);
    format = format == null ? null : java.util.Map.copyOf(format);
    options = options == null ? null : java.util.Map.copyOf(options);
  }

  public record Message(String role, String content) {}
}
