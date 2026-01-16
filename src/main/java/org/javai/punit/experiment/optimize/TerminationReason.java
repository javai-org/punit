package org.javai.punit.experiment.optimize;

/**
 * Reason for optimization termination, included in final history.
 *
 * @param cause the category of termination
 * @param message human-readable description
 */
public record TerminationReason(
        TerminationCause cause,
        String message
) {
    /**
     * Creates a TerminationReason with validation.
     */
    public TerminationReason {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
    }

    /**
     * Creates a termination reason for reaching max iterations.
     *
     * @param maxIterations the configured maximum
     * @return a new TerminationReason
     */
    public static TerminationReason maxIterations(int maxIterations) {
        return new TerminationReason(
                TerminationCause.MAX_ITERATIONS,
                "Reached maximum iterations: " + maxIterations
        );
    }

    /**
     * Creates a termination reason for no improvement.
     *
     * @param windowSize the no-improvement window size
     * @return a new TerminationReason
     */
    public static TerminationReason noImprovement(int windowSize) {
        return new TerminationReason(
                TerminationCause.NO_IMPROVEMENT,
                "No improvement in last " + windowSize + " iterations"
        );
    }

    /**
     * Creates a termination reason for time budget exhaustion.
     *
     * @param budgetMs the time budget in milliseconds
     * @return a new TerminationReason
     */
    public static TerminationReason timeBudgetExhausted(long budgetMs) {
        return new TerminationReason(
                TerminationCause.TIME_BUDGET_EXHAUSTED,
                "Time budget exhausted: " + budgetMs + "ms"
        );
    }

    /**
     * Creates a termination reason for mutation failure.
     *
     * @param errorMessage the error from the mutator
     * @return a new TerminationReason
     */
    public static TerminationReason mutationFailure(String errorMessage) {
        return new TerminationReason(
                TerminationCause.MUTATION_FAILURE,
                "Mutation failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for scoring failure.
     *
     * @param errorMessage the error from the scorer
     * @return a new TerminationReason
     */
    public static TerminationReason scoringFailure(String errorMessage) {
        return new TerminationReason(
                TerminationCause.SCORING_FAILURE,
                "Scoring failed: " + errorMessage
        );
    }
}
