package org.javai.punit.api.typed.spec;

import java.util.List;

/**
 * Produces the next factors for an {@link Experiment} iteration
 * given the current factors and the history of scored iterations
 * so far. The returned factors are passed to the use case factory
 * to construct the next iteration's use case instance.
 *
 * <p>An optimize search walks through the factor space; each
 * invocation is one step in that walk. Implementations encode the
 * search strategy — gradient descent, hill climbing, random walk,
 * genetic-algorithm variation, etc.
 *
 * @param <FT> the factor record type
 */
@FunctionalInterface
public interface FactorsStepper<FT> {

    /**
     * @param current the last factors evaluated
     * @param history the full iteration history in execution order
     * @return the next factors to evaluate, or {@code null} to
     *         signal "no more candidates" (the optimiser will stop)
     */
    FT next(FT current, List<IterationResult<FT>> history);

    /**
     * One entry in the optimize history fed to {@link #next}.
     *
     * @param factors the factors evaluated in that iteration
     * @param score the score produced by the {@link Scorer} for that iteration
     * @param <FT> the factor record type
     */
    record IterationResult<FT>(FT factors, double score) {}
}
