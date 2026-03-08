package org.javai.punit.experiment.engine.input;

/**
 * Exception thrown when input source resolution fails.
 *
 * <p>This exception indicates problems with:
 * <ul>
 *   <li>Invalid {@code @InputSource} annotation configuration</li>
 *   <li>Method source not found or not accessible</li>
 *   <li>File source not found or parse errors</li>
 * </ul>
 */
public class InputSourceException extends RuntimeException {

    /**
     * Creates an exception with the specified message.
     *
     * @param message the detail message
     */
    public InputSourceException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public InputSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
