package com.openprompt.opa;

/**
 * Execution modes for OPA archives as defined in the specification.
 */
public enum ExecutionMode {
    INTERACTIVE("interactive"),
    BATCH("batch"),
    AUTONOMOUS("autonomous");

    private final String value;

    ExecutionMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ExecutionMode fromValue(String value) {
        if (value == null) {
            return INTERACTIVE;
        }
        for (ExecutionMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown execution mode: " + value);
    }
}
