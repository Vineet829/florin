package com.florin.document;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DocumentState {
    DRAFT,
    ISSUED,
    VOID;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DocumentState fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
