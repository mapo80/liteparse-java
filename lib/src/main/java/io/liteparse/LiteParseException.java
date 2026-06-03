package io.liteparse;

/**
 * Unchecked exception thrown when a native LiteParse operation fails
 * (parsing error, invalid input, native library failure, etc.).
 */
public class LiteParseException extends RuntimeException {

    public LiteParseException(String message) {
        super(message);
    }

    public LiteParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
