package io.qwenbridge.semantic.ai;

import org.springframework.stereotype.Component;

@Component
public class SemanticPromptBuilder {

  public String build(String query) {
    return """
           Analyze the semantic meaning of the following search query.

           Return only valid JSON.
           Do not wrap the response in markdown.
           Do not include explanations.
           Do not include comments.

           Use exactly this JSON structure:
           {
             "originalQuery": "...",
             "normalizedQuery": "...",
             "semanticMeaning": "...",
             "entities": [
               {
                 "value": "...",
                 "type": "PRODUCT",
                 "confidence": 0.0
               }
             ],
             "domainHints": ["..."],
             "ambiguity": {
               "ambiguous": false,
               "possibleMeanings": []
             },
             "confidence": 0.0
           }

           Entity type rules:
           - type must be exactly one of:
             PRODUCT
             BRAND
             CATEGORY
             ATTRIBUTE
             PRICE
             LOCATION
             LANGUAGE
             UNKNOWN
           - type must never contain multiple values.
           - never return values like PRODUCT|CATEGORY.
           - if unsure, use UNKNOWN.

           Confidence rules:
           - confidence must be a number between 0.0 and 1.0.
           - entity confidence must also be between 0.0 and 1.0.

           Query:
           %s
           """
        .formatted(query);
  }
}
