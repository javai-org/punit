package org.javai.punit.experiment.optimize;

import java.util.Optional;

/**
 * Terminates if no improvement in the last N iterations.
 *
 * <p>Improvement is determined by comparing iteration scores against the
 * best score seen so far, respecting the optimization objective.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Stop if no improvement for 5 consecutive iterations
 * OptimizeTerminationPolicy policy = new OptimizationNoImprovementPolicy(5);
 * }</pre>
 */
public final class OptimizationNoImprovementPolicy implements OptimizeTerminationPolicy {

    private final int windowSize;

    /**
     * Creates a policy that terminates after windowSize iterations without improvement.
     *
     * @param windowSize the number of iterations without improvement before terminating
     * @throws IllegalArgumentException if windowSize is less than 1
     */
    public OptimizationNoImprovementPolicy(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be at least 1");
        }
        this.windowSize = windowSize;
    }

    @Override
    public Optional<OptimizeTerminationReason> shouldTerminate(OptimizeHistory history) {
        // Need at least windowSize + 1 iterations to evaluate
        if (history.iterationCount() <= windowSize) {
            return Optional.empty();
        }

        Optional<OptimizationRecord> bestOpt = history.bestIteration();
        if (bestOpt.isEmpty()) {
            // No successful iterations yet, don't terminate
            return Optional.empty();
        }

        int bestIterationNumber = bestOpt.get().iterationNumber();
        int currentIterationNumber = history.iterationCount() - 1;
        int iterationsSinceBest = currentIterationNumber - bestIterationNumber;

        if (iterationsSinceBest >= windowSize) {
            return Optional.of(OptimizeTerminationReason.noImprovement(windowSize));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "No improvement for " + windowSize + " iterations";
    }
}
