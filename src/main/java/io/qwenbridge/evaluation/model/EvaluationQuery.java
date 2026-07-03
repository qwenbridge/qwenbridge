package io.qwenbridge.evaluation.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record EvaluationQuery(
        String id,
        String query,
        List<RelevanceLabel> labels
) {

    public EvaluationQuery {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        labels = List.copyOf(Objects.requireNonNull(labels, "labels must not be null"));
    }

    public Map<String, RelevanceLabel> labelsByDocumentId() {
        return labels.stream()
                .collect(Collectors.toUnmodifiableMap(
                        RelevanceLabel::documentId,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    public long relevantDocumentCount() {
        return labels.stream()
                .filter(RelevanceLabel::relevant)
                .count();
    }
}
