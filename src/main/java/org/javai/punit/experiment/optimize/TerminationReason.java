package org.javai.punit.experiment.optimize;

/**
 * Reason for optimization termination, included in final history.
 *
 * <p>This record wraps a {@link org.javai.punit.model.TerminationReason} cause
 * with a detailed message for auditability.
 *
 * @param cause the category of termination (uses framework-wide enum)
 * @param message human-readable description
 */
public record TerminationReason(
        org.javai.punit.model.TerminationReason cause,
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
                org.javai.punit.model.TerminationReason.MAX_ITERATIONS,
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
                org.javai.punit.model.TerminationReason.NO_IMPROVEMENT,
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
                org.javai.punit.model.TerminationReason.OPTIMIZATION_TIME_BUDGET_EXHAUSTED,
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
                org.javai.punit.model.TerminationReason.MUTATION_FAILURE,
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
                org.javai.punit.model.TerminationReason.SCORING_FAILURE,
                "Scoring failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for score threshold reached.
     *
     * @param threshold the threshold that was reached
     * @param achievedScore the score that was achieved
     * @return a new TerminationReason
     */
    public static TerminationReason scoreThresholdReached(double threshold, double achievedScore) {
        return new TerminationReason(
                org.javai.punit.model.TerminationReason.SCORE_THRESHOLD_REACHED,
                String.format("Score threshold %.4f reached with score %.4f", threshold, achievedScore)
        );
    }
}
