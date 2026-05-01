package org.javai.punit.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.spec.ExceptionPolicy;

/**
 * Describes <em>how</em> to produce samples — the use case factory,
 * the input cycle, the sample count, and the sample-loop governors
 * (budgets, exception policy, failure-retention cap).
 *
 * <h2>Sampling is the integrity mechanism for the empirical pair
 * pattern</h2>
 *
 * <p>The headline use of {@code Sampling} is to be authored once,
 * in a helper method, and consumed by both a measure baseline and
 * the probabilistic test paired against it. <strong>That sharing is
 * not just a code-reuse convenience — it is the framework's
 * structural guarantee that the empirical comparison is
 * meaningful.</strong>
 *
 * <p>An empirical probabilistic test asks: <em>has the service's
 * pass rate degraded from the rate the baseline measured?</em> For
 * that question to be statistically coherent, the test and the
 * baseline must be drawn from the same sampling population — same
 * use case, same input list, same factors, same sample-loop
 * governors. Anything that differs between them confounds the
 * comparison. By passing the same {@code Sampling} value into both
 * the measure and the test, Java's reference semantics enforce that
 * sameness at compile time. The pairing is structural, not a
 * brittle prose convention.
 *
 * <p>A {@code Sampling} does not carry a factors instance — factors
 * are supplied at the spec entry point that consumes the sampling
 * ({@code Experiment.measuring(sampling, factors)},
 * {@code ProbabilisticTest.testing(sampling, factors)}, etc.). For
 * the measure / test pair, both call sites pass the same factors;
 * the framework's empirical-baseline resolver then matches the
 * test's factors against the measure's stored baseline (CR02).
 *
 * <h2>The pair pattern at the call site</h2>
 *
 * <pre>{@code
 * private Sampling<F, I, O> sampling(int samples) {
 *     return Sampling.of(f -> new MyUseCase(f), samples, input1, input2);
 * }
 *
 * @PUnitExperiment Experiment baseline() {
 *     return Experiment.measuring(sampling(1000), factors).build();
 * }
 * @PUnitTest ProbabilisticTest meets() {
 *     return ProbabilisticTest.testing(sampling(100), factors)
 *             .criterion(BernoulliPassRate.empirical())
 *             .build();
 * }
 * }</pre>
 *
 * <p>Same {@code sampling(...)} helper, same {@code factors},
 * differing only in the sample count and the criterion overlay.
 * The <em>same Sampling, same factors</em> shape is the contract
 * the empirical baseline resolver depends on.
 *
 * <h2>Other consumers</h2>
 *
 * <pre>{@code
 * @PUnitExperiment Experiment compare() {
 *     return Experiment.exploring(sampling(50)).grid(a, b, c).build();
 * }
 * @PUnitExperiment Experiment tune() {
 *     return Experiment.optimizing(sampling(20))
 *             .initialFactors(f0).stepper(...).maximize(...).build();
 * }
 * }</pre>
 *
 * <p>Explore and optimize use the same Sampling-then-factors-vary
 * shape, with factors generated either from a grid or by an
 * iterative stepper. The "Sampling stays constant; factors vary"
 * principle is what makes the per-configuration comparison
 * meaningful — same as the measure ↔ test pairing, scaled out to
 * many configurations.
 *
 * <h2>Two construction paths</h2>
 *
 * <ul>
 *   <li>{@link #of(Function, int, List) Sampling.of(useCase, samples, inputs)}
 *       — compact form for the common case. Defaults applied for all
 *       optional knobs (no budgets, {@code FAIL} on exhaustion,
 *       {@code ABORT_TEST} on exception, {@code maxExampleFailures = 10}).</li>
 *   <li>{@link #builder()} — full control over budgets, policies, and
 *       failure-retention. The 20% case.</li>
 * </ul>
 */
public final class Sampling<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final InputSupplier<IT> inputs;
    private final int samples;
    private final Optional<Duration> timeBudget;
    private final OptionalLong tokenBudget;
    private final long tokenCharge;
    private final BudgetExhaustionPolicy budgetPolicy;
    private final ExceptionPolicy exceptionPolicy;
    private final int maxExampleFailures;
    private final List<OT> expected;
    private final Optional<ValueMatcher<OT>> matcher;

    private Sampling(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.inputs = b.inputs;
        this.samples = b.samples;
        this.timeBudget = b.timeBudget;
        this.tokenBudget = b.tokenBudget;
        this.tokenCharge = b.tokenCharge;
        this.budgetPolicy = b.budgetPolicy;
        this.exceptionPolicy = b.exceptionPolicy;
        this.maxExampleFailures = b.maxExampleFailures;
        this.expected = b.expected;
        this.matcher = b.matcher;
    }

    public static <FT, IT, OT> Builder<FT, IT, OT> builder() {
        return new Builder<>();
    }

    /**
     * Compact form for the common case — three required values, all
     * optional knobs (budgets, exception policy, failure-retention cap)
     * left at their defaults. Equivalent to:
     *
     * <pre>{@code
     * Sampling.<FT, IT, OT>builder()
     *         .useCaseFactory(useCaseFactory)
     *         .inputs(inputs)
     *         .samples(samples)
     *         .build();
     * }</pre>
     *
     * <p>For samplings that need a time / token budget, a non-default
     * exception policy, or a custom failure-retention cap, use the
     * full {@link #builder()} form. The two paths are bit-for-bit
     * equivalent at the data level — {@code of(...)} simply skips the
     * builder ceremony for the 80% case.
     */
    public static <FT, IT, OT> Sampling<FT, IT, OT> of(
            Function<FT, UseCase<FT, IT, OT>> useCaseFactory,
            int samples,
            List<IT> inputs) {
        return Sampling.<FT, IT, OT>builder()
                .useCaseFactory(useCaseFactory)
                .inputs(inputs)
                .samples(samples)
                .build();
    }

    /**
     * Varargs form of {@link #of(Function, int, List)} — convenient
     * when the inputs are inline literals.
     */
    @SafeVarargs
    public static <FT, IT, OT> Sampling<FT, IT, OT> of(
            Function<FT, UseCase<FT, IT, OT>> useCaseFactory,
            int samples,
            IT... inputs) {
        Objects.requireNonNull(inputs, "inputs");
        return of(useCaseFactory, samples, List.of(inputs));
    }

    public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    /**
     * The inputs themselves, materialised through the underlying
     * supplier. Equivalent to {@code inputSupplier().all()}.
     */
    public List<IT> inputs() {
        return inputs.all();
    }

    /**
     * The underlying {@link InputSupplier} — the unit of
     * sampling-frame identity for empirical pairing.
     *
     * @see InputSupplier
     */
    public InputSupplier<IT> inputSupplier() {
        return inputs;
    }

    /**
     * Convenience accessor: the supplier's {@link InputSupplier#identity()
     * identity} string.
     */
    public String inputsIdentity() {
        return inputs.identity();
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
     * The expected output values, paired by index with {@link #inputs()}.
     * Empty when no instance-conformance matching is configured.
     *
     * <p>Set via {@link Builder#matching(List)} /
     * {@link Builder#matching(List, ValueMatcher)}. When non-empty,
     * the engine pairs each input with its expected value at sample
     * time and runs {@link #matcher()} (or
     * {@link ValueMatcher#equality()}) to compare against the
     * use case's produced value.
     */
    public List<OT> expected() {
        return expected;
    }

    /**
     * The matcher to use against {@link #expected()}, if configured.
     * Defaults to {@link ValueMatcher#equality()} when
     * {@link Builder#matching(List)} is used; explicitly supplied via
     * {@link Builder#matching(List, ValueMatcher)} otherwise. Empty
     * when {@link #expected()} is empty.
     */
    public Optional<ValueMatcher<OT>> matcher() {
        return matcher;
    }

    /**
     * Returns a new sampling with the sample count replaced. Supports
     * the confidence-first authoring pattern:
     * {@code PowerAnalysis.sampleSize(...)} computes an {@code int},
     * which this wither stamps onto a template sampling.
     */
    public Sampling<FT, IT, OT> samples(int samples) {
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
        b.expected = this.expected;
        b.matcher = this.matcher;
        return b;
    }

    public static final class Builder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private InputSupplier<IT> inputs;
        private int samples = 1000;
        private Optional<Duration> timeBudget = Optional.empty();
        private OptionalLong tokenBudget = OptionalLong.empty();
        private long tokenCharge = 0L;
        private BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
        private ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
        private int maxExampleFailures = 10;
        private List<OT> expected = List.of();
        private Optional<ValueMatcher<OT>> matcher = Optional.empty();

        private Builder() {}

        public Builder<FT, IT, OT> useCaseFactory(Function<FT, UseCase<FT, IT, OT>> factory) {
            this.useCaseFactory = Objects.requireNonNull(factory, "useCaseFactory");
            return this;
        }

        /**
         * Supply inputs as inline values. The framework wraps the
         * values in an {@link InputSupplier} whose identity is a
         * deterministic content hash — same machinery as
         * {@link #inputs(InputSupplier)} consuming an
         * {@link InputSupplier#from(java.util.function.Supplier) InputSupplier.from(...)}
         * value, just sugar at the call site.
         */
        public Builder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            List<IT> snapshot = List.copyOf(inputs);
            return inputs(InputSupplier.from(() -> snapshot));
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        /**
         * Supply inputs from an {@link InputSupplier}. Used for
         * file-backed fixtures, computed datasets, or any source
         * that lives behind a {@code Supplier}. The framework
         * computes a content hash of the materialised list — identity
         * is always content-derived. See
         * {@code docs/DES-INPUTS-IDENTITY-SUPPLIER.md}.
         */
        public Builder<FT, IT, OT> inputs(InputSupplier<IT> supplier) {
            this.inputs = Objects.requireNonNull(supplier, "inputs");
            return this;
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

        /**
         * Configures instance-conformance matching: pairs each input
         * with the expected value at the same index, and uses the
         * default {@link ValueMatcher#equality() equality matcher} to
         * compare the use case's produced value against it.
         *
         * <p>For richer comparisons (case-insensitive string equality,
         * JSON structural equivalence, …) use the
         * {@link #matching(List, ValueMatcher) two-arg form}.
         *
         * @param expectedOutputs values paired by index with
         *                        {@link #inputs(List)}; must be the
         *                        same length as the input list at
         *                        {@link #build()} time
         * @throws NullPointerException if {@code expectedOutputs} is null
         */
        public Builder<FT, IT, OT> matching(List<OT> expectedOutputs) {
            return matching(expectedOutputs, ValueMatcher.equality());
        }

        /**
         * Configures instance-conformance matching with a custom
         * matcher. The matcher is invoked per sample against
         * {@code expected[i]} and the use case's produced value.
         *
         * @param expectedOutputs values paired by index with
         *                        {@link #inputs(List)}; must be the
         *                        same length as the input list at
         *                        {@link #build()} time
         * @param valueMatcher    the comparison function
         * @throws NullPointerException if either argument is null
         */
        public Builder<FT, IT, OT> matching(List<OT> expectedOutputs,
                                            ValueMatcher<OT> valueMatcher) {
            Objects.requireNonNull(expectedOutputs, "expectedOutputs");
            Objects.requireNonNull(valueMatcher, "matcher");
            this.expected = List.copyOf(expectedOutputs);
            this.matcher = Optional.of(valueMatcher);
            return this;
        }

        public Sampling<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (inputs == null) {
                throw new IllegalStateException("inputs is required");
            }
            return new Sampling<>(this);
        }
    }

}
