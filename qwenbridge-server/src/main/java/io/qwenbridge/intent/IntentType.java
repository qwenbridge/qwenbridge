package io.qwenbridge.intent;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum IntentType {

    PRODUCT_SEARCH,
    NAVIGATION,
    FILTER,
    COMPARE,
    UNKNOWN;

    @JsonCreator
    public static IntentType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String normalized = value.trim().toUpperCase();

        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
