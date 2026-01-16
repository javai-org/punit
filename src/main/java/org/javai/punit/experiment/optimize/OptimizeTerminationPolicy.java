package org.javai.punit.experiment.optimize;

import java.util.Optional;

/**
 * Determines when optimization should terminate.
 *
 * <p>Common termination conditions:
 * <ul>
 *   <li>Maximum iterations reached</li>
 *   <li>No improvement for N consecutive iterations</li>
 *   <li>Budget exhausted (time, tokens, cost)</li>
 *   <li>Score threshold achieved</li>
 * </ul>
 *
 * <p>Policies are composable via {@code OptimizeCompositeTerminationPolicy}.
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Return {@link Optional#empty()} to continue optimization</li>
 *   <li>Return a {@link OptimizeTerminationReason} to stop</li>
 *   <li>Use only the history for decisions (no side effects)</li>
 * </ul>
 */
@FunctionalInterface
public interface OptimizeTerminationPolicy {

    /**
     * Check if optimization should terminate.
     *
     * @param history current optimization history
     * @return termination reason if should stop, empty to continue
     */
    Optional<OptimizeTerminationReason> shouldTerminate(OptimizeHistory history);

    /**
     * Human-readable description for the optimization history.
     *
     * @return description of the termination criteria
     */
    default String description() {
        return this.getClass().getSimpleName();
    }
}
