package org.javai.punit.experiment.optimize;

/**
 * Evaluates an iteration's aggregate and produces a scalar score.
 *
 * <p>The scorer is the automated equivalent of the human evaluator in EXPLORE mode.
 * Where EXPLORE produces specs for human comparison via diff, OPTIMIZE uses
 * the scorer to programmatically determine which factor value performs best.
 *
 * <h2>Score Normalization</h2>
 * <p>Scores <b>must be normalized to the range 0.0 to 1.0</b> (inclusive), where:
 * <ul>
 *   <li>{@code 0.0} = worst possible outcome</li>
 *   <li>{@code 1.0} = best possible outcome</li>
 * </ul>
 *
 * <p>This normalization enables:
 * <ul>
 *   <li>Consistent percentage display in output (e.g., "85.0%")</li>
 *   <li>Meaningful comparison across different experiments</li>
 *   <li>Intuitive threshold configuration (e.g., 0.5 = 50% minimum)</li>
 * </ul>
 *
 * <p>Scores must be comparable:
 * <ul>
 *   <li>For {@link OptimizationObjective#MAXIMIZE}: higher score = better</li>
 *   <li>For {@link OptimizationObjective#MINIMIZE}: lower score = better</li>
 * </ul>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Normalize raw metrics to 0.0-1.0 (e.g., success rate is already normalized)</li>
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
     * Compute a normalized score for the given aggregate.
     *
     * <p>The aggregate contains:
     * <ul>
     *   <li>All factor values (fixed + treatment) for context</li>
     *   <li>Statistics aggregated from N outcomes</li>
     * </ul>
     *
     * @param aggregate the iteration's aggregate result
     * @return a normalized score in the range 0.0 to 1.0 (displayed as percentage)
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

    /**
     * Minimum acceptable score threshold.
     *
     * <p>Iterations with scores below this threshold are marked as
     * {@link OptimizeStatus#BELOW_THRESHOLD} rather than {@link OptimizeStatus#SUCCESS}.
     * This helps identify configurations that don't meet minimum quality standards
     * even when they score and execute correctly.
     *
     * <p>By default, returns {@link Double#NEGATIVE_INFINITY} which means
     * all valid scores are acceptable.
     *
     * @return the minimum acceptable score (for MAXIMIZE) or maximum (for MINIMIZE)
     */
    default double minimumAcceptanceThreshold() {
        return Double.NEGATIVE_INFINITY;
    }
}
