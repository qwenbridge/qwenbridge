package io.qwenbridge.evaluation.model;

public record RelevanceLabel(
        String documentId,
        int relevance
) {

    public RelevanceLabel {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }

        if (relevance < 0) {
            relevance = 0;
        }
    }

    public boolean relevant() {
        return relevance > 0;
    }
}
