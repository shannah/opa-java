package com.openprompt.opa;

/**
 * Exception thrown when an OPA archive is invalid or cannot be processed.
 */
public class OpaException extends Exception {

    public OpaException(String message) {
        super(message);
    }

    public OpaException(String message, Throwable cause) {
        super(message, cause);
    }
}
