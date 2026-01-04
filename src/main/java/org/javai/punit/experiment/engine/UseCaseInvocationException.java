package org.javai.punit.experiment.engine;

/**
 * Exception thrown when a use case invocation fails.
 *
 * <p>This exception is thrown when:
 * <ul>
 *   <li>The use case method cannot be accessed</li>
 *   <li>The use case method returns null or an unexpected type</li>
 *   <li>The use case method throws a checked exception</li>
 * </ul>
 */
public class UseCaseInvocationException extends RuntimeException {
    
    /**
     * Creates a new exception with the specified message.
     *
     * @param message the detail message
     */
    public UseCaseInvocationException(String message) {
        super(message);
    }
    
    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public UseCaseInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

