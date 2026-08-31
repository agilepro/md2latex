package com.purplehillsbooks.md2latex;

/**
 * Raised when a manifest is missing, malformed, or internally inconsistent.
 *
 * <p>Manifests are hand written, so the message is the user interface: it must
 * say which file, which key, and what was expected.
 */
public class ManifestException extends Exception {

    private static final long serialVersionUID = 1L;

    public ManifestException(String message) {
        super(message);
    }

    public ManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
