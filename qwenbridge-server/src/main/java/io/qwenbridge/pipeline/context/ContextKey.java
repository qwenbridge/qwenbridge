package io.qwenbridge.pipeline.context;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ContextKey<T> {

    @EqualsAndHashCode.Include
    private final String name;

    private final Class<T> type;

    private ContextKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public static <T> ContextKey<T> of(String name, Class<T> type) {
        return new ContextKey<>(name, type);
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "ContextKey[name=%s, type=%s]"
                .formatted(name, type.getSimpleName());
    }
}
