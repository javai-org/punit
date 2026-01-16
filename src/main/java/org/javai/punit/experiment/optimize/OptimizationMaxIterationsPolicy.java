package org.javai.punit.experiment.optimize;

import java.util.Optional;

/**
 * Terminates after a fixed number of iterations.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * OptimizeTerminationPolicy policy = new OptimizationMaxIterationsPolicy(20);
 * }</pre>
 */
public final class OptimizationMaxIterationsPolicy implements OptimizeTerminationPolicy {

    private final int maxIterations;

    /**
     * Creates a policy that terminates after maxIterations.
     *
     * @param maxIterations the maximum number of iterations
     * @throws IllegalArgumentException if maxIterations is less than 1
     */
    public OptimizationMaxIterationsPolicy(int maxIterations) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.maxIterations = maxIterations;
    }

    @Override
    public Optional<OptimizeTerminationReason> shouldTerminate(OptimizeHistory history) {
        if (history.iterationCount() >= maxIterations) {
            return Optional.of(OptimizeTerminationReason.maxIterations(maxIterations));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Max " + maxIterations + " iterations";
    }
}
