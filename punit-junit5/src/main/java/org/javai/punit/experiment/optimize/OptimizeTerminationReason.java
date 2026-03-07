package org.javai.punit.experiment.optimize;

import org.javai.punit.model.TerminationReason;

/**
 * Reason for optimization termination, included in final history.
 *
 * <p>This record wraps a {@link TerminationReason} cause
 * with a detailed message for auditability.
 *
 * @param cause the category of termination (uses framework-wide enum)
 * @param message human-readable description
 */
public record OptimizeTerminationReason(
        TerminationReason cause,
        String message
) {
    /**
     * Creates an OptimizeTerminationReason with validation.
     */
    public OptimizeTerminationReason {
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
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason maxIterations(int maxIterations) {
        return new OptimizeTerminationReason(
                TerminationReason.MAX_ITERATIONS,
                "Reached maximum iterations: " + maxIterations
        );
    }

    /**
     * Creates a termination reason for no improvement.
     *
     * @param windowSize the no-improvement window size
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason noImprovement(int windowSize) {
        return new OptimizeTerminationReason(
                TerminationReason.NO_IMPROVEMENT,
                "No improvement in last " + windowSize + " iterations"
        );
    }

    /**
     * Creates a termination reason for time budget exhaustion.
     *
     * @param budgetMs the time budget in milliseconds
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason timeBudgetExhausted(long budgetMs) {
        return new OptimizeTerminationReason(
                TerminationReason.OPTIMIZATION_TIME_BUDGET_EXHAUSTED,
                "Time budget exhausted: " + budgetMs + "ms"
        );
    }

    /**
     * Creates a termination reason for mutation failure.
     *
     * @param errorMessage the error from the mutator
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason mutationFailure(String errorMessage) {
        return new OptimizeTerminationReason(
                TerminationReason.MUTATION_FAILURE,
                "Mutation failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for scoring failure.
     *
     * @param errorMessage the error from the scorer
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason scoringFailure(String errorMessage) {
        return new OptimizeTerminationReason(
                TerminationReason.SCORING_FAILURE,
                "Scoring failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for score threshold reached.
     *
     * @param threshold the threshold that was reached
     * @param achievedScore the score that was achieved
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason scoreThresholdReached(double threshold, double achievedScore) {
        return new OptimizeTerminationReason(
                TerminationReason.SCORE_THRESHOLD_REACHED,
                String.format("Score threshold %.4f reached with score %.4f", threshold, achievedScore)
        );
    }

    /**
     * Creates a termination reason for normal completion (all iterations ran).
     *
     * @return a new OptimizeTerminationReason
     */
    public static OptimizeTerminationReason completed() {
        return new OptimizeTerminationReason(
                TerminationReason.COMPLETED,
                "All iterations completed"
        );
    }
}
