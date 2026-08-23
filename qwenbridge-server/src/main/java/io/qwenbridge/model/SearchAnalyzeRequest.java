package io.qwenbridge.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SearchAnalyzeRequest(
    String requestId,
    @NotBlank(message = "query must not be blank") String query,
    @Pattern(
            regexp = "^[A-Za-z]{2}$",
            message = "declaredLanguage must be a two-letter language code")
        String declaredLanguage,
    @Pattern(
            regexp = "^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$",
            message = "locale must be a supported BCP 47 language tag")
        String locale) {

  public SearchAnalyzeRequest(String requestId, String query) {
    this(requestId, query, null, null);
  }
}
