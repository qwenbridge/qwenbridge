package io.qwenbridge.pipeline.context;

import java.util.Objects;

public final class ContextKey<T> {

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
    public boolean equals(Object object) {

        if (this == object) {
            return true;
        }

        if (!(object instanceof ContextKey<?> other)) {
            return false;
        }

        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ContextKey[name=%s, type=%s]"
                .formatted(name, type.getSimpleName());
    }

}