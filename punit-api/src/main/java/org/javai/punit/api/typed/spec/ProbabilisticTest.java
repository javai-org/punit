package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test: runs a {@link Sampling} bound to a factor
 * bundle and evaluates one or more {@link Criterion} values against
 * the resulting sample summary.
 *
 * <p>Constructed via {@link #testing(Sampling, Object)}. The sample
 * production is governed by the factor-free {@link Sampling}; the
 * second argument is the factor bundle the test runs against. The
 * claims the spec makes about the resulting population are expressed
 * by the criteria registered via {@link Builder#criterion(Criterion)}
 * (REQUIRED) and {@link Builder#reportOnly(Criterion)} (REPORT_ONLY).
 * The combined verdict is {@link Verdict#compose(List)}'d from the
 * REQUIRED subset.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed {@link Builder}, and the engine
 * recovers the typed view via {@link Spec#dispatch(Dispatcher)}.
 */
public final class ProbabilisticTest implements Spec {

    private final Internal<?, ?, ?> internal;

    private ProbabilisticTest(Internal<?, ?, ?> internal) {
        this.internal = internal;
    }

    /**
     * Entry point — compose a probabilistic test over a
     * {@link Sampling} and the factors it should run against.
     *
     * <p>For an empirical test (criterion built via
     * {@code BernoulliPassRate.empirical()} or {@code .empiricalFrom(...)}),
     * the {@code Sampling} passed here must be the <em>same value</em>
     * passed to the paired measure's
     * {@link Experiment#measuring(Sampling, Object) Experiment.measuring(...)}.
     * The shared reference is what guarantees the test and the
     * baseline are drawn from the same sampling population — same
     * use case, same input list, same governors. Without that
     * sameness, the empirical comparison is statistically
     * incoherent.
     *
     * <p>Authoring pattern: extract the {@code Sampling} into a
     * helper method shared between the measure and the test:
     *
     * <pre>{@code
     * private Sampling<F, I, O> sampling(int samples) { ... }
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
     * <p>Same {@code factors} at both call sites; same helper
     * supplying the {@code Sampling}; only the sample count and the
     * criterion overlay differ.
     */
    public static <FT, IT, OT> Builder<FT, IT, OT> testing(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new Builder<>(
                Objects.requireNonNull(sampling, "sampling"),
                Objects.requireNonNull(factors, "factors"));
    }

    /**
     * Inline-sampling form of {@link #testing(Sampling, Object)}.
     * Sampling parameters are supplied through the returned builder.
     *
     * <p>For a probabilistic test with a <strong>contractual</strong>
     * criterion (e.g., {@code BernoulliPassRate.meeting(threshold, origin)}),
     * the inline form is equivalent to constructing a fresh
     * {@link Sampling} per spec — there is no baseline pairing to
     * preserve, so no integrity guarantee at risk.
     *
     * <p>For a probabilistic test with an <strong>empirical</strong>
     * criterion ({@code BernoulliPassRate.empirical()} /
     * {@code .empiricalFrom(...)}), the inline form is rejected at
     * {@code .build()} time. An empirical comparison requires the
     * test and the baseline measure to draw from the same sampling
     * population; that integrity guarantee comes from sharing a
     * {@link Sampling} value with the paired measure, which the
     * inline form cannot provide. The diagnostic teaches the
     * helper-extraction pattern.
     */
    public static <FT, IT, OT> InlineBuilder<FT, IT, OT> testing(
            Function<FT, UseCase<FT, IT, OT>> useCaseFactory, FT factors) {
        return new InlineBuilder<>(
                Objects.requireNonNull(useCaseFactory, "useCaseFactory"),
                Objects.requireNonNull(factors, "factors"));
    }

    /** Sample count for this test, taken from its sampling. */
    public int samples() { return internal.sampling.samples(); }

    /**
     * The test's declared intent. {@link TestIntent#VERIFICATION} (default)
     * claims evidential status; {@link TestIntent#SMOKE} is a
     * sentinel-grade lightweight check.
     */
    public TestIntent intent() { return internal.intent; }

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return doDispatch(internal, dispatcher);
    }

    private static <FT, IT, OT, R> R doDispatch(Internal<FT, IT, OT> typed, Dispatcher<R> d) {
        return d.apply(typed);
    }

    // ── Typed internal delegate (engine-facing) ─────────────────────

    private static final class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered;
        private final TestIntent intent;

        private SampleSummary<OT> summary;

        private Internal(Builder<FT, IT, OT> b) {
            this.sampling = b.sampling;
            this.factors = b.factors;
            this.registered = List.copyOf(b.registered);
            this.intent = b.intent;
        }

        @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
            return sampling.useCaseFactory();
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return List.of(Configuration.<FT, IT, OT>of(factors, sampling.inputs(), sampling.samples()))
                    .iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            this.summary = summary;
        }

        @Override public EngineResult conclude(BaselineProvider provider) {
            Objects.requireNonNull(provider, "provider");
            SampleSummary<OT> s = summary != null
                    ? summary
                    : SampleSummary.from(List.of(), Duration.ZERO);
            FactorBundle factorBundle = FactorBundle.of(factors);
            String useCaseId = sampling.useCaseFactory().apply(factors).id();
            String testInputsIdentity = sampling.inputsIdentity();
            // Looked up once and reused across criteria — the identity is a
            // property of the baseline file, not of any individual criterion.
            Optional<String> baselineInputsIdentity = anyEmpiricalCriterion()
                    ? provider.baselineInputsIdentityFor(useCaseId, factorBundle)
                    : Optional.empty();

            List<EvaluatedCriterion> evaluated = new ArrayList<>(registered.size());
            // De-duplicated misalignment notes — selection runs once per
            // empirical criterion against the same file, so each criterion
            // accumulates the same notes; recording per-criterion would
            // produce N copies of every reason. The notes describe the
            // baseline file's misalignment, not any one criterion's.
            java.util.LinkedHashSet<String> warnings = new java.util.LinkedHashSet<>();
            // The matched baseline's covariate profile. All empirical
            // criteria that resolve a baseline for the same
            // (useCaseId, factorsFingerprint) tuple resolve the same
            // file, so the first non-empty profile we see is the one
            // for the run. Stamped onto the result so verdict
            // renderers / HTML emitters can compare against the
            // observed profile (post-stamped at the JUnit boundary).
            org.javai.punit.api.typed.covariate.CovariateProfile baselineProfile =
                    org.javai.punit.api.typed.covariate.CovariateProfile.empty();
            for (Registered<OT> entry : registered) {
                BaselineLookupCapture capture = new BaselineLookupCapture();
                CriterionResult result = evaluate(
                        entry.criterion(), s, factorBundle, useCaseId,
                        testInputsIdentity, baselineInputsIdentity,
                        provider, warnings, capture);
                evaluated.add(new EvaluatedCriterion(result, entry.role()));
                if (baselineProfile.isEmpty() && !capture.profile.isEmpty()) {
                    baselineProfile = capture.profile;
                }
            }

            Verdict composed = Verdict.compose(evaluated);
            return new ProbabilisticTestResult(
                    composed, factorBundle, evaluated, intent,
                    List.copyOf(warnings),
                    org.javai.punit.api.typed.covariate.CovariateAlignment.compute(
                            org.javai.punit.api.typed.covariate.CovariateProfile.empty(),
                            baselineProfile),
                    Optional.empty(),
                    s.failuresByPostcondition());
        }

        /**
         * Side channel for {@link #evaluate} to surface the matched
         * baseline's covariate profile back to {@link #conclude}.
         * Kept off {@link CriterionResult} because the matched-baseline
         * profile is a lookup-side concern, not a criterion-semantic
         * concern — every empirical criterion that resolves a
         * baseline for the same {@code (useCaseId, factorsFingerprint)}
         * tuple sees the same profile, so the data belongs to the
         * run, not to any one criterion.
         */
        private static final class BaselineLookupCapture {
            org.javai.punit.api.typed.covariate.CovariateProfile profile =
                    org.javai.punit.api.typed.covariate.CovariateProfile.empty();
        }

        private boolean anyEmpiricalCriterion() {
            for (Registered<OT> entry : registered) {
                if (entry.criterion().isEmpirical()) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CriterionResult evaluate(
                Criterion<OT, ?> criterion,
                SampleSummary<OT> s,
                FactorBundle factorBundle,
                String useCaseId,
                String testInputsIdentity,
                Optional<String> baselineInputsIdentity,
                BaselineProvider provider,
                java.util.Set<String> warnings,
                BaselineLookupCapture capture) {
            Criterion raw = criterion;
            Optional<? extends BaselineStatistics> resolved;
            if (criterion.isEmpirical()) {
                BaselineLookup<? extends BaselineStatistics> lookup = provider.baselineLookup(
                        useCaseId, factorBundle, criterion.name(), criterion.statisticsType());
                resolved = lookup.selected();
                warnings.addAll(lookup.notes());
                capture.profile = lookup.baselineProfile();
            } else {
                resolved = Optional.empty();
            }
            EvaluationContext ctx = new EvaluationContext<OT, BaselineStatistics>() {
                @Override public SampleSummary<OT> summary() { return s; }
                @Override public Optional<BaselineStatistics> baseline() {
                    return (Optional<BaselineStatistics>) resolved;
                }
                @Override public FactorBundle factors() { return factorBundle; }
                @Override public String testInputsIdentity() { return testInputsIdentity; }
                @Override public Optional<String> baselineInputsIdentity() {
                    return baselineInputsIdentity;
                }
            };
            return (CriterionResult) raw.evaluate(ctx);
        }

        @Override public Optional<Duration> timeBudget() { return sampling.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return sampling.tokenBudget(); }
        @Override public long tokenCharge() { return sampling.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return sampling.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return sampling.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return sampling.maxExampleFailures(); }
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;

        private Builder(Sampling<FT, IT, OT> sampling, FT factors) {
            this.sampling = sampling;
            this.factors = factors;
        }

        /** Register a criterion that contributes to the combined verdict. */
        public Builder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REQUIRED));
            return this;
        }

        /** Register a criterion whose result is attached but excluded from composition. */
        public Builder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REPORT_ONLY));
            return this;
        }

        /**
         * Declares the test's intent. Defaults to
         * {@link TestIntent#VERIFICATION}. Authors of sentinel-grade
         * smoke checks against external providers opt into
         * {@link TestIntent#SMOKE} explicitly.
         */
        public Builder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTest build() {
            return new ProbabilisticTest(new Internal<>(this));
        }
    }

    private record Registered<OT>(Criterion<OT, ?> criterion, CriterionRole role) {}

    // ── Inline-sampling builder ─────────────────────────────────────

    /**
     * Inline-form builder. Accumulates sampling-level state alongside
     * the test-overlay state; synthesises a {@link Sampling} at
     * {@link #build()} time. Rejects empirical criteria at build time
     * with a diagnostic teaching the helper-extraction pattern.
     */
    public static final class InlineBuilder<FT, IT, OT> {

        private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private final FT factors;
        private List<IT> inputs;
        private int samples = 1000;
        private Optional<Duration> timeBudget = Optional.empty();
        private OptionalLong tokenBudget = OptionalLong.empty();
        private long tokenCharge = 0L;
        private BudgetExhaustionPolicy budgetPolicy = BudgetExhaustionPolicy.FAIL;
        private ExceptionPolicy exceptionPolicy = ExceptionPolicy.ABORT_TEST;
        private int maxExampleFailures = 10;
        private final List<Registered<OT>> registered = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;

        private InlineBuilder(Function<FT, UseCase<FT, IT, OT>> useCaseFactory, FT factors) {
            this.useCaseFactory = useCaseFactory;
            this.factors = factors;
        }

        public InlineBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("inputs must be non-empty");
            }
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final InlineBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(Objects.requireNonNull(inputs, "inputs")));
        }

        public InlineBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + samples);
            }
            this.samples = samples;
            return this;
        }

        public InlineBuilder<FT, IT, OT> timeBudget(Duration budget) {
            this.timeBudget = Optional.of(Objects.requireNonNull(budget, "timeBudget"));
            return this;
        }

        public InlineBuilder<FT, IT, OT> tokenBudget(long tokens) {
            this.tokenBudget = OptionalLong.of(tokens);
            return this;
        }

        public InlineBuilder<FT, IT, OT> tokenCharge(long tokens) {
            this.tokenCharge = tokens;
            return this;
        }

        public InlineBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            this.budgetPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            this.exceptionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public InlineBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            this.maxExampleFailures = cap;
            return this;
        }

        public InlineBuilder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REQUIRED));
            return this;
        }

        public InlineBuilder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REPORT_ONLY));
            return this;
        }

        public InlineBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTest build() {
            // Empirical-criterion guard. The integrity guarantee for an
            // empirical comparison comes from sharing a Sampling value
            // with the paired measure; the inline form constructs a
            // fresh Sampling per spec and cannot provide that guarantee.
            for (Registered<OT> r : registered) {
                if (r.criterion().isEmpirical()) {
                    throw new IllegalStateException(
                            "An empirical criterion (" + r.criterion().name() + ") requires "
                                    + "the probabilistic test to share a Sampling with its "
                                    + "baseline measure. The inline ProbabilisticTest.testing("
                                    + "useCaseFactory, factors) form cannot guarantee that "
                                    + "structural pairing.\n\n"
                                    + "Extract the sampling as a helper and use the "
                                    + "Sampling-bound entry point at both call sites:\n"
                                    + "    private Sampling<F, I, O> sampling(int samples) {\n"
                                    + "        return Sampling.of(useCaseFactory, samples, inputs);\n"
                                    + "    }\n"
                                    + "    @PUnitExperiment Experiment baseline() {\n"
                                    + "        return Experiment.measuring(sampling(1000), factors).build();\n"
                                    + "    }\n"
                                    + "    @PUnitTest ProbabilisticTest meets() {\n"
                                    + "        return ProbabilisticTest.testing(sampling(100), factors)\n"
                                    + "                .criterion(BernoulliPassRate.empirical())\n"
                                    + "                .build();\n"
                                    + "    }");
                }
            }
            if (inputs == null) {
                throw new IllegalStateException(
                        "inputs is required — call .inputs(...) before .build()");
            }
            Sampling.Builder<FT, IT, OT> sb = Sampling.<FT, IT, OT>builder()
                    .useCaseFactory(useCaseFactory)
                    .inputs(inputs)
                    .samples(samples);
            timeBudget.ifPresent(sb::timeBudget);
            tokenBudget.ifPresent(sb::tokenBudget);
            if (tokenCharge > 0L) {
                sb.tokenCharge(tokenCharge);
            }
            sb.onBudgetExhausted(budgetPolicy);
            sb.onException(exceptionPolicy);
            sb.maxExampleFailures(maxExampleFailures);
            Sampling<FT, IT, OT> sampling = sb.build();

            Builder<FT, IT, OT> delegate = new Builder<>(sampling, factors);
            delegate.intent(intent);
            for (Registered<OT> r : registered) {
                if (r.role() == CriterionRole.REQUIRED) {
                    delegate.criterion(r.criterion());
                } else {
                    delegate.reportOnly(r.criterion());
                }
            }
            return delegate.build();
        }
    }
}
