package io.qwenbridge.ai.contract;

public record StreamingChatRequest(String prompt, java.util.Map<String, Object> outputSchema) {
  public StreamingChatRequest(String prompt) {
    this(prompt, null);
  }

  public StreamingChatRequest {
    outputSchema = outputSchema == null ? null : java.util.Map.copyOf(outputSchema);
  }
}
