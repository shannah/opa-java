package ca.weblite.opa;

/**
 * Message roles in OPA session history.
 */
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");

    private final String value;

    MessageRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageRole fromValue(String value) {
        for (MessageRole role : values()) {
            if (role.value.equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown message role: " + value);
    }
}
