package io.omnisearch.ai.value;

import java.util.Objects;

public record ModelId(String value) {

    public ModelId {
        value = Objects.requireNonNull(value, "model id must not be null")
                .trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
