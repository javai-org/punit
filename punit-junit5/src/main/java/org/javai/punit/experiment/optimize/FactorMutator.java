package org.javai.punit.experiment.optimize;

/**
 * Generates new values for the treatment factor.
 *
 * <p>The mutator implements the search strategy - how to explore the space
 * of possible values for the factor being optimized. Strategies range
 * from simple (random perturbation) to sophisticated (LLM-based rewriting).
 *
 * <p>The mutator only changes the treatment factor. All other factors
 * remain constant throughout optimization.
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Use the history to inform mutations (e.g., learn from failures)</li>
 *   <li>Implement {@link #validate(Object)} to enforce constraints</li>
 *   <li>Throw {@link MutationException} on unrecoverable failures</li>
 * </ul>
 *
 * @param <F> The type of the factor being optimized (String, Image, etc.)
 */
@FunctionalInterface
public interface FactorMutator<F> {

    /**
     * Generate a new value for the treatment factor.
     *
     * @param currentValue the current value of the treatment factor
     * @param history read-only access to optimization history
     * @return a new value to try in the next iteration
     * @throws MutationException if mutation fails (optimization may terminate)
     */
    F mutate(F currentValue, OptimizeHistory history) throws MutationException;

    /**
     * Human-readable description for the optimization history.
     *
     * @return description of the mutation strategy
     */
    default String description() {
        return this.getClass().getSimpleName();
    }

    /**
     * Validate a factor value before use.
     *
     * <p>Override to add constraints (e.g., max length, content filters).
     * Called after mutation to ensure the new value is acceptable.
     *
     * @param value the value to validate
     * @throws MutationException if the value violates constraints
     */
    default void validate(F value) throws MutationException {
        // No validation by default
    }
}
