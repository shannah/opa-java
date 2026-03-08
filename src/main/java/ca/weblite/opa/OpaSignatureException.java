package ca.weblite.opa;

/**
 * Thrown when signature verification fails on an OPA archive.
 */
public class OpaSignatureException extends OpaException {

    public OpaSignatureException(String message) {
        super(message);
    }

    public OpaSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
