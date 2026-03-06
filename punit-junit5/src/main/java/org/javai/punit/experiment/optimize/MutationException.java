package org.javai.punit.experiment.optimize;

/**
 * Thrown when mutation fails.
 *
 * <p>When a mutator throws this exception, the optimization terminates
 * with {@link org.javai.punit.model.TerminationReason#MUTATION_FAILURE} and returns partial results.
 */
public class MutationException extends Exception {

    /**
     * Creates a MutationException with the given message.
     *
     * @param message description of what went wrong
     */
    public MutationException(String message) {
        super(message);
    }

    /**
     * Creates a MutationException with the given message and cause.
     *
     * @param message description of what went wrong
     * @param cause the underlying exception
     */
    public MutationException(String message, Throwable cause) {
        super(message, cause);
    }
}
