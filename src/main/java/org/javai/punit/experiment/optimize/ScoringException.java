package org.javai.punit.experiment.optimize;

/**
 * Thrown when scoring fails.
 *
 * <p>When a scorer throws this exception, the iteration is marked as failed
 * with {@link OptimizeStatus#SCORING_FAILED} and the optimization may terminate
 * depending on the termination policy.
 */
public class ScoringException extends Exception {

    /**
     * Creates a ScoringException with the given message.
     *
     * @param message description of what went wrong
     */
    public ScoringException(String message) {
        super(message);
    }

    /**
     * Creates a ScoringException with the given message and cause.
     *
     * @param message description of what went wrong
     * @param cause the underlying exception
     */
    public ScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
