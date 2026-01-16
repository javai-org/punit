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
public record OptimizationTerminationReason(
        TerminationReason cause,
        String message
) {
    /**
     * Creates an OptimizationTerminationReason with validation.
     */
    public OptimizationTerminationReason {
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
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason maxIterations(int maxIterations) {
        return new OptimizationTerminationReason(
                TerminationReason.MAX_ITERATIONS,
                "Reached maximum iterations: " + maxIterations
        );
    }

    /**
     * Creates a termination reason for no improvement.
     *
     * @param windowSize the no-improvement window size
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason noImprovement(int windowSize) {
        return new OptimizationTerminationReason(
                TerminationReason.NO_IMPROVEMENT,
                "No improvement in last " + windowSize + " iterations"
        );
    }

    /**
     * Creates a termination reason for time budget exhaustion.
     *
     * @param budgetMs the time budget in milliseconds
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason timeBudgetExhausted(long budgetMs) {
        return new OptimizationTerminationReason(
                TerminationReason.OPTIMIZATION_TIME_BUDGET_EXHAUSTED,
                "Time budget exhausted: " + budgetMs + "ms"
        );
    }

    /**
     * Creates a termination reason for mutation failure.
     *
     * @param errorMessage the error from the mutator
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason mutationFailure(String errorMessage) {
        return new OptimizationTerminationReason(
                TerminationReason.MUTATION_FAILURE,
                "Mutation failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for scoring failure.
     *
     * @param errorMessage the error from the scorer
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason scoringFailure(String errorMessage) {
        return new OptimizationTerminationReason(
                TerminationReason.SCORING_FAILURE,
                "Scoring failed: " + errorMessage
        );
    }

    /**
     * Creates a termination reason for score threshold reached.
     *
     * @param threshold the threshold that was reached
     * @param achievedScore the score that was achieved
     * @return a new OptimizationTerminationReason
     */
    public static OptimizationTerminationReason scoreThresholdReached(double threshold, double achievedScore) {
        return new OptimizationTerminationReason(
                TerminationReason.SCORE_THRESHOLD_REACHED,
                String.format("Score threshold %.4f reached with score %.4f", threshold, achievedScore)
        );
    }
}
