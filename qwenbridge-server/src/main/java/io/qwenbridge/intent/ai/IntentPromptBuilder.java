package io.qwenbridge.intent.ai;

import org.springframework.stereotype.Component;

@Component
public class IntentPromptBuilder {

  public String build(String query) {
    return """
           Classify the intent of the following search query.

           Return only valid JSON.
           Do not wrap the response in markdown.
           Do not include explanations.
           Do not include comments.

           Use exactly this JSON structure:
           {
             "type": "PRODUCT_SEARCH",
             "reason": "...",
             "confidence": 0.0
           }

           Intent type rules:
           - type must be exactly one of:
             PRODUCT_SEARCH
             NAVIGATION
             FILTER
             COMPARE
             UNKNOWN
           - type must never contain multiple values.
           - if unsure, use UNKNOWN.

           Confidence rules:
           - confidence must be a number between 0.0 and 1.0.

           Query:
           %s
           """
        .formatted(query);
  }
}
