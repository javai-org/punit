package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * <p>{@link #failuresByPostcondition()} carries the per-clause
     * failure counts and bounded exemplars from the iteration's
     * {@link SampleSummary}, so a meta-LLM driving the search can
     * compose a meaningful improvement prompt that says <em>which</em>
     * postconditions tripped and <em>what inputs</em> tripped them.
     *
     * <p>{@link #failureExemplars()} is a bounded flattened view of
     * the same data — the union of all per-clause exemplars across
     * all clauses, useful when the search wants to see "concrete
     * failed samples" without bucketing.
     *
     * @param factors the factors evaluated in that iteration
     * @param score the score produced by the {@link Scorer}
     * @param failuresByPostcondition per-clause failure counts +
     *                                bounded exemplars
     * @param failureExemplars        flattened bounded view across
     *                                all clauses
     * @param <FT> the factor record type
     */
    record IterationResult<FT>(
            FT factors,
            double score,
            Map<String, FailureCount> failuresByPostcondition,
            List<FailureExemplar> failureExemplars) {

        public IterationResult {
            Objects.requireNonNull(failuresByPostcondition, "failuresByPostcondition");
            Objects.requireNonNull(failureExemplars, "failureExemplars");
            failuresByPostcondition = Map.copyOf(failuresByPostcondition);
            failureExemplars = List.copyOf(failureExemplars);
        }

        /**
         * Backward-compatible constructor that defaults the histogram
         * and exemplar list to empty. Used by call sites that haven't
         * yet migrated to the canonical 4-field shape; the engine
         * constructs results via the canonical constructor with
         * populated histograms.
         */
        public IterationResult(FT factors, double score) {
            this(factors, score, Map.of(), List.of());
        }
    }
}
