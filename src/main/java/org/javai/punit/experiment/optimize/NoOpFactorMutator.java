package org.javai.punit.experiment.optimize;

/**
 * No-op mutator that returns the value unchanged.
 *
 * <p>Primarily useful for testing the optimization framework without
 * actual mutations.
 *
 * @param <F> the type of the factor
 */
public final class NoOpFactorMutator<F> implements FactorMutator<F> {

    @Override
    public F mutate(F currentValue, OptimizeHistory history) {
        return currentValue;
    }

    @Override
    public String description() {
        return "No-op (value unchanged)";
    }
}
