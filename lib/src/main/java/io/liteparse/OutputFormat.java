package io.liteparse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Output format for parsing. */
public enum OutputFormat {
    JSON("json"),
    TEXT("text");

    private final String wire;

    OutputFormat(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static OutputFormat fromWire(String value) {
        if (value == null) {
            return null;
        }
        return "text".equalsIgnoreCase(value) ? TEXT : JSON;
    }
}
