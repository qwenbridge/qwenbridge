package io.qwenbridge.model;

import io.qwenbridge.core.translation.TextRepresentations;
import io.qwenbridge.pipeline.result.TranslationStepResult;
import java.util.List;
import java.util.Map;

/**
 * Public view of the query's text forms (QB-017): the original, the search-normalized form and the
 * accepted translation, each derivation listed with the component that applied it.
 */
public record TextRepresentationsResponse(
    String original, String normalized, String translated, List<Transformation> transformations) {

  public TextRepresentationsResponse {
    transformations = transformations == null ? List.of() : List.copyOf(transformations);
  }

  public record Transformation(
      String kind, String from, String to, String actor, Map<String, String> details) {
    public Transformation {
      details = details == null ? Map.of() : Map.copyOf(details);
    }
  }

  public static TextRepresentationsResponse from(TranslationStepResult step) {
    if (step == null) {
      return null;
    }
    TextRepresentations text = step.text();
    return new TextRepresentationsResponse(
        text.original(),
        text.normalized().orElse(null),
        text.translated().map(TextRepresentations.TranslatedText::text).orElse(null),
        text.transformations().stream()
            .map(t -> new Transformation(t.kind().name(), t.from(), t.to(), t.actor(), t.details()))
            .toList());
  }
}
