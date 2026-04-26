package org.javai.punit.api.typed;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.typed.spec.ExceptionPolicy;

/**
 * A {@link SamplingShape} bound to one factor bundle.
 *
 * <p>Produced only by {@link SamplingShape#factors(Object)}; there is no
 * public constructor or builder. Measure and probabilistic-test
 * specs consume a {@code DataGeneration} via their
 * {@code .measuring(plan)} / {@code .testing(plan)} entry points.
 *
 * <p>Pass-through accessors for the shape's fields let consumers
 * read the sample-loop parameters directly without going through
 * {@link #shape()}.
 */
public final class DataGeneration<FT, IT, OT> {

    private final SamplingShape<FT, IT, OT> shape;
    private final FT factors;

    DataGeneration(SamplingShape<FT, IT, OT> shape, FT factors) {
        this.shape = Objects.requireNonNull(shape, "shape");
        this.factors = Objects.requireNonNull(factors, "factors");
    }

    public SamplingShape<FT, IT, OT> shape() {
        return shape;
    }

    public FT factors() {
        return factors;
    }

    public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return shape.useCaseFactory();
    }

    public List<IT> inputs() {
        return shape.inputs();
    }

    public int samples() {
        return shape.samples();
    }

    public Optional<Duration> timeBudget() {
        return shape.timeBudget();
    }

    public OptionalLong tokenBudget() {
        return shape.tokenBudget();
    }

    public long tokenCharge() {
        return shape.tokenCharge();
    }

    public BudgetExhaustionPolicy budgetPolicy() {
        return shape.budgetPolicy();
    }

    public ExceptionPolicy exceptionPolicy() {
        return shape.exceptionPolicy();
    }

    public int maxExampleFailures() {
        return shape.maxExampleFailures();
    }
}
