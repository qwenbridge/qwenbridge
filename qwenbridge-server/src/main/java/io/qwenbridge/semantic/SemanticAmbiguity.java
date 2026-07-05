package io.qwenbridge.semantic;

import java.util.List;

public record SemanticAmbiguity(boolean ambiguous, List<String> possibleMeanings) {

  public SemanticAmbiguity {
    possibleMeanings = possibleMeanings == null ? List.of() : List.copyOf(possibleMeanings);
  }

  public static SemanticAmbiguity none() {
    return new SemanticAmbiguity(false, List.of());
  }
}
