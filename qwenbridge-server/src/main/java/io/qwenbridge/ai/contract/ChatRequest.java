package io.qwenbridge.ai.contract;

public record ChatRequest(String prompt, java.util.Map<String, Object> outputSchema) {
  public ChatRequest(String prompt) {
    this(prompt, null);
  }

  public ChatRequest {
    outputSchema = outputSchema == null ? null : java.util.Map.copyOf(outputSchema);
  }
}
