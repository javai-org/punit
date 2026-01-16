package org.javai.punit.experiment.optimize;

import java.util.Optional;

/**
 * Terminates after a fixed number of iterations.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TerminationPolicy policy = new MaxIterationsPolicy(20);
 * }</pre>
 */
public final class MaxIterationsPolicy implements TerminationPolicy {

    private final int maxIterations;

    /**
     * Creates a policy that terminates after maxIterations.
     *
     * @param maxIterations the maximum number of iterations
     * @throws IllegalArgumentException if maxIterations is less than 1
     */
    public MaxIterationsPolicy(int maxIterations) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.maxIterations = maxIterations;
    }

    @Override
    public Optional<TerminationReason> shouldTerminate(OptimizationHistory history) {
        if (history.iterationCount() >= maxIterations) {
            return Optional.of(TerminationReason.maxIterations(maxIterations));
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Max " + maxIterations + " iterations";
    }
}
