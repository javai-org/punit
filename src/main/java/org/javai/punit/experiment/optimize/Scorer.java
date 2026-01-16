package org.javai.punit.experiment.optimize;

/**
 * Evaluates an iteration's aggregate and produces a scalar score.
 *
 * <p>The scorer is the automated equivalent of the human evaluator in EXPLORE mode.
 * Where EXPLORE produces specs for human comparison via diff, OPTIMIZE uses
 * the scorer to programmatically determine which factor value performs best.
 *
 * <p>Scores must be comparable:
 * <ul>
 *   <li>For {@link OptimizationObjective#MAXIMIZE}: higher score = better</li>
 *   <li>For {@link OptimizationObjective#MINIMIZE}: lower score = better</li>
 * </ul>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Return consistent scores for identical inputs</li>
 *   <li>Avoid side effects</li>
 *   <li>Throw {@link ScoringException} on failure rather than returning magic values</li>
 * </ul>
 *
 * @param <A> The aggregate type (typically {@link OptimizationIterationAggregate})
 */
@FunctionalInterface
public interface Scorer<A> {

    /**
     * Compute a scalar score for the given aggregate.
     *
     * <p>The aggregate contains:
     * <ul>
     *   <li>All factor values (fixed + treatment) for context</li>
     *   <li>Statistics aggregated from N outcomes</li>
     * </ul>
     *
     * @param aggregate the iteration's aggregate result
     * @return a scalar score for ranking this iteration
     * @throws ScoringException if scoring fails
     */
    double score(A aggregate) throws ScoringException;

    /**
     * Human-readable description for the optimization history.
     *
     * @return description of the scoring strategy
     */
    default String description() {
        return this.getClass().getSimpleName();
    }
}
