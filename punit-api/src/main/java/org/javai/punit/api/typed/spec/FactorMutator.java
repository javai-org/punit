package org.javai.punit.api.typed.spec;

import java.util.List;

/**
 * Produces the next factor record for an {@link Experiment}
 * iteration given the current record and the history of scored
 * iterations so far.
 *
 * @param <FT> the factor record type
 */
@FunctionalInterface
public interface FactorMutator<FT> {

    /**
     * @param current the last factor record explored
     * @param history the full iteration history in execution order
     * @return the next factor record to explore, or {@code null} to
     *         signal "no more candidates" (the optimiser will stop)
     */
    FT next(FT current, List<IterationResult<FT>> history);

    /**
     * One entry in the optimise history fed to {@link #next}.
     *
     * @param factors the factors explored in that iteration
     * @param score the score produced by the {@link Scorer} for that iteration
     * @param <FT> the factor record type
     */
    record IterationResult<FT>(FT factors, double score) {}
}
