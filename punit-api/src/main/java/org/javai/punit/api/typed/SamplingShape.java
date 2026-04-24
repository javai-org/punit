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
 * A factor-free description of <em>how</em> to produce samples.
 *
 * <p>A shape carries the use case factory, the input cycle, the
 * sample count, and the sample-loop governors (budgets, exception
 * policy, failure-retention cap) — everything needed to drive the
 * sampling loop once a factor bundle is bound.
 *
 * <p>Binding a factor bundle is deliberately a separate step:
 * {@link #at(Object) shape.at(factors)} yields a
 * {@link DataGeneration} that measure and probabilistic-test specs
 * consume, while explore and optimize specs consume the shape
 * directly. One shape, four consumers, no factor-drift opportunity.
 */
public final class SamplingShape<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final List<IT> inputs;
    private final int samples;
    private final Optional<Duration> timeBudget;
    private final OptionalLong tokenBudget;
    private final long tokenCharge;
    private final BudgetExhaustionPolicy budgetPolicy;
    private final ExceptionPolicy exceptionPolicy;
    private final int maxExampleFailures;

    private SamplingShape(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.inputs = b.inputs;
        this.samples = b.samples;
        this.timeBudget = b.timeBudget;
        this.tokenBudget = b.tokenBudget;
        this.tokenCharge = b.tokenCharge;
        this.budgetPolicy = b.budgetPolicy;
        this.exceptionPolicy = b.exceptionPolicy;
        this.maxExampleFailures = b.maxExampleFailures;
    }

    public static <FT, IT, OT> Builder<FT, IT, OT> builder() {
        return new Builder<>();
    }

    public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    public List<IT> inputs() {
        return inputs;
    }

    public int samples() {
        return samples;
    }

    public Optional<Duration> timeBudget() {
        return timeBudget;
    }

    public OptionalLong tokenBudget() {
        return tokenBudget;
    }

    public long tokenCharge() {
        return tokenCharge;
    }

    public BudgetExhaustionPolicy budgetPolicy() {
        return budgetPolicy;
    }

    public ExceptionPolicy exceptionPolicy() {
        return exceptionPolicy;
    }

    public int maxExampleFailures() {
        return maxExampleFailures;
    }

    /**
     * Bind a factor bundle to this shape, producing a
     * single-configuration {@link DataGeneration}. The shape is
     * unchanged; a different bundle may be bound independently.
     */
    public DataGeneration<FT, IT, OT> at(FT factors) {
        Objects.requireNonNull(factors, "factors");
        return new DataGeneration<>(this, factors);
    }

    /**
     * Returns a new shape with the sample count replaced. Supports the
     * confidence-first authoring pattern:
     * {@code PowerAnalysis.sampleSize(...)} computes an {@code int},
     * which this wither then stamps onto a template shape.
     */
    public SamplingShape<FT, IT, OT> samples(int samples) {
        if (samples < 1) {
            throw new IllegalArgumentException("samples must be >= 1, got " + samples);
        }
        Builder<FT, IT, OT> b = copyToBuilder();
        b.samples = samples;
        return b.build();
    }

    private Builder<FT, IT, OT> copyToBuilder() {
        Builder<FT, IT, OT> b = new Builder<>();
        b.useCaseFactory = this.useCaseFactory;
        b.inputs = this.inputs;
        b.samples = this.samples;
        b.timeBudget = this.timeBudget;
        b.tokenBudget = this.tokenBudget;
        b.tokenCharge = this.tokenCharge;
        b.budgetPolicy = this.budgetPolicy;
        b.exceptionPolicy = this.exceptionPolicy;
        b.maxExampleFailures = this.maxExampleFailures;
        return b;
    }

    public static final class Builder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private List<IT> inputs;
        private int samples = 1000;
        private Optional<Duration> timeBudget = Optional.empty();
        private OptionalLong tokenBudget = OptionalLong.empty();
        private long tokenCharge = 0L;
        private BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
        private ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
        private int maxExampleFailures = 10;

        private Builder() {}

        public Builder<FT, IT, OT> useCaseFactory(Function<FT, UseCase<FT, IT, OT>> factory) {
            this.useCaseFactory = Objects.requireNonNull(factory, "useCaseFactory");
            return this;
        }

        public Builder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> inputs(IT... inputs) {
            Objects.requireNonNull(inputs, "inputs");
            return inputs(List.of(inputs));
        }

        public Builder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            this.samples = samples;
            return this;
        }

        public Builder<FT, IT, OT> timeBudget(Duration budget) {
            Objects.requireNonNull(budget, "timeBudget");
            if (budget.isZero() || budget.isNegative()) {
                throw new IllegalArgumentException("timeBudget must be > 0, got " + budget);
            }
            this.timeBudget = Optional.of(budget);
            return this;
        }

        public Builder<FT, IT, OT> tokenBudget(long tokens) {
            if (tokens <= 0) {
                throw new IllegalArgumentException("tokenBudget must be > 0, got " + tokens);
            }
            this.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public Builder<FT, IT, OT> tokenCharge(long tokens) {
            if (tokens < 0) {
                throw new IllegalArgumentException("tokenCharge must be non-negative, got " + tokens);
            }
            this.tokenCharge = tokens;
            return this;
        }

        public Builder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            this.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder<FT, IT, OT> onException(ExceptionPolicy policy) {
            this.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder<FT, IT, OT> maxExampleFailures(int cap) {
            if (cap < 0) {
                throw new IllegalArgumentException(
                        "maxExampleFailures must be non-negative, got " + cap);
            }
            this.maxExampleFailures = cap;
            return this;
        }

        public SamplingShape<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (inputs == null) {
                throw new IllegalStateException("inputs is required");
            }
            return new SamplingShape<>(this);
        }
    }
}
