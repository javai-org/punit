package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Supplier;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test whose per-sample outcomes are Bernoulli
 * trials: every sample is a binary pass/fail against the service's
 * contract, and the functional verdict is synthesised from the
 * observed pass rate of N such trials.
 *
 * <p>This makes {@code ProbabilisticTestSpec} the <em>Bernoulli
 * embodiment</em> of the probabilistic-test concept. Other
 * statistical models — collision probability for randomly-generated
 * identifiers, expected-value tests over continuous outputs,
 * distributional goodness-of-fit — belong in sibling spec types
 * with their own verdict-synthesis logic. Those siblings share the
 * same engine, the same {@link DataGenerationSpec} strategy
 * contract, and the same latency machinery, and differ only in how
 * they turn a stream of samples into a functional verdict.
 *
 * <p>Configured via one of three statistical approaches, all three
 * Bernoulli-specific:
 *
 * <ul>
 *   <li><b>Threshold-first</b> — {@link #normative()} — a normative
 *       pass-rate threshold is declared alongside a sample size.</li>
 *   <li><b>Sample-size-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#samples(int)} — sample count declared,
 *       threshold derived from the baseline's observed pass rate at
 *       verdict time.</li>
 *   <li><b>Confidence-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#minDetectableEffect(double)} /
 *       {@link EmpiricalBuilder#power(double)} — sample count
 *       computed from a power analysis over a difference in
 *       proportions.</li>
 * </ul>
 *
 * <p>Builder constraints enforced at compile time by the type split:
 * calling {@code .threshold(...)} requires {@link NormativeBuilder};
 * calling {@code .minDetectableEffect(...)} / {@code .power(...)}
 * requires {@link EmpiricalBuilder}. Combinations that mix the two
 * paths do not compile.
 *
 * <p>Verdict synthesis is currently a placeholder that evaluates
 * "observed pass rate ≥ threshold → PASS"; Stage 4 replaces it with
 * Wilson-score-based evaluation and, for the confidence-first path,
 * a real power analysis. The Bernoulli-specific step is isolated in
 * {@link #bernoulliFunctionalVerdict()}.
 */
public final class ProbabilisticTestSpec<FT, IT, OT> implements DataGenerationSpec<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final FT factors;
    private final List<IT> inputs;
    private final int samples;
    private final double threshold;
    private final ThresholdOrigin thresholdOrigin;
    private final String contractRef;
    private final TestIntent intent;
    private final List<String> defaultWarnings;
    private final ResourceControls resourceControls;
    private final LatencySpec latency;
    private final Optional<VerdictDimension> assertOnOverride;

    private SampleSummary<OT> summary;

    private ProbabilisticTestSpec(
            Function<FT, UseCase<FT, IT, OT>> useCaseFactory,
            FT factors,
            List<IT> inputs,
            int samples,
            double threshold,
            ThresholdOrigin thresholdOrigin,
            String contractRef,
            TestIntent intent,
            List<String> defaultWarnings,
            ResourceControls resourceControls,
            LatencySpec latency,
            Optional<VerdictDimension> assertOnOverride) {
        this.useCaseFactory = useCaseFactory;
        this.factors = factors;
        this.inputs = inputs;
        this.samples = samples;
        this.threshold = threshold;
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.intent = intent;
        this.defaultWarnings = defaultWarnings;
        this.resourceControls = resourceControls;
        this.latency = latency;
        this.assertOnOverride = assertOnOverride;
    }

    public static <FT, IT, OT> NormativeBuilder<FT, IT, OT> normative() {
        return new NormativeBuilder<>();
    }

    public static <FT, IT, OT> EmpiricalBuilder<FT, IT, OT> basedOn(
            Supplier<MeasureSpec<FT, IT, OT>> baseline) {
        return new EmpiricalBuilder<>(baseline);
    }

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return List.of(Configuration.<FT, IT, OT>of(factors, inputs, samples)).iterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        this.summary = summary;
    }

    @Override public EngineResult conclude() {
        int successes = summary == null ? 0 : summary.successes();
        int failures = summary == null ? 0 : summary.failures();
        Verdict functionalVerdict = bernoulliFunctionalVerdict();
        Optional<LatencyVerdict> latencyVerdictOpt = summary == null
                ? Optional.empty()
                : LatencyVerdict.evaluate(latency, summary.latencyResult());
        Verdict projected = Verdict.project(functionalVerdict, latencyVerdictOpt, assertOn());

        List<String> warnings = new ArrayList<>(defaultWarnings);
        warnings.add("statistics pending Stage 4 — verdict uses placeholder threshold comparison");

        return new ProbabilisticTestResult(
                        projected,
                        FactorBundle.of(factors),
                        successes,
                        failures,
                        threshold,
                        thresholdOrigin,
                        warnings,
                        latencyVerdictOpt);
    }

    /**
     * Functional verdict for the Bernoulli-trial model: every sample
     * is a binary success/failure; the observed pass rate is compared
     * to the declared threshold.
     *
     * <p>This is the model-specific step — the only part of
     * {@link #conclude()} that ties this spec type to the Bernoulli
     * trial. Latency evaluation and verdict projection are delegated
     * to {@link LatencyVerdict#evaluate} and {@link Verdict#project},
     * both model-agnostic.
     *
     * <p>Placeholder until Stage 4 replaces the inequality with a
     * Wilson-score-based evaluation.
     */
    private Verdict bernoulliFunctionalVerdict() {
        if (summary == null || summary.total() == 0) {
            return Verdict.INCONCLUSIVE;
        }
        return summary.passRate() >= threshold ? Verdict.PASS : Verdict.FAIL;
    }

    public double threshold() { return threshold; }
    public ThresholdOrigin thresholdOrigin() { return thresholdOrigin; }
    public String contractRef() { return contractRef; }
    public TestIntent intent() { return intent; }
    public int samples() { return samples; }

    // ── Stage-3 spec-interface accessors ─────────────────────────────

    @Override public Optional<Duration> timeBudget() { return resourceControls.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return resourceControls.tokenBudget(); }
    @Override public long tokenCharge() { return resourceControls.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return resourceControls.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return resourceControls.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return resourceControls.maxExampleFailures(); }
    @Override public LatencySpec latency() { return latency; }

    /**
     * Which verdict dimension projects through the probabilistic
     * test's single-valued outcome. Derived from the spec's latency
     * configuration when not explicitly set:
     *
     * <ul>
     *   <li>{@link VerdictDimension#BOTH} when a non-disabled
     *       {@link LatencySpec} is declared;</li>
     *   <li>{@link VerdictDimension#FUNCTIONAL} otherwise.</li>
     * </ul>
     */
    public VerdictDimension assertOn() {
        if (assertOnOverride.isPresent()) {
            return assertOnOverride.get();
        }
        return latency.hasAnyThreshold()
                ? VerdictDimension.BOTH
                : VerdictDimension.FUNCTIONAL;
    }

    // ── NormativeBuilder (threshold-first) ─────────────────────────

    public static final class NormativeBuilder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private FT factors;
        private List<IT> inputs;
        private int samples = 100;
        private double threshold = Double.NaN;
        private ThresholdOrigin thresholdOrigin;
        private String contractRef;
        private TestIntent intent = TestIntent.VERIFICATION;
        private final ResourceControlsBuilder resources = new ResourceControlsBuilder();
        private LatencySpec latency = LatencySpec.disabled();
        private VerdictDimension assertOnOverride;

        private NormativeBuilder() {}

        public NormativeBuilder<FT, IT, OT> timeBudget(Duration budget) {
            resources.timeBudget(budget);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> tokenBudget(long tokens) {
            resources.tokenBudget(tokens);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> tokenCharge(long tokens) {
            resources.tokenCharge(tokens);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            resources.onBudgetExhausted(policy);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            resources.onException(policy);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            resources.maxExampleFailures(cap);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> latency(LatencySpec spec) {
            this.latency = Objects.requireNonNull(spec, "latency");
            return this;
        }

        public NormativeBuilder<FT, IT, OT> assertOn(VerdictDimension dimension) {
            this.assertOnOverride = Objects.requireNonNull(dimension, "dimension");
            return this;
        }

        public NormativeBuilder<FT, IT, OT> useCaseFactory(
                Function<FT, UseCase<FT, IT, OT>> factory) {
            this.useCaseFactory = Objects.requireNonNull(factory, "useCaseFactory");
            return this;
        }

        public NormativeBuilder<FT, IT, OT> factors(FT factors) {
            this.factors = Objects.requireNonNull(factors, "factors");
            return this;
        }

        public NormativeBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final NormativeBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(inputs));
        }

        public NormativeBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) throw new IllegalArgumentException("samples must be ≥ 1");
            this.samples = samples;
            return this;
        }

        public NormativeBuilder<FT, IT, OT> threshold(double threshold, ThresholdOrigin origin) {
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException(
                        "threshold must be in [0, 1], got " + threshold);
            }
            Objects.requireNonNull(origin, "thresholdOrigin");
            if (origin == ThresholdOrigin.EMPIRICAL) {
                throw new IllegalArgumentException(
                        "normative builder rejects EMPIRICAL origin — "
                                + "use basedOn(...) to derive a threshold from a baseline");
            }
            this.threshold = threshold;
            this.thresholdOrigin = origin;
            return this;
        }

        public NormativeBuilder<FT, IT, OT> contractRef(String ref) {
            this.contractRef = ref;
            return this;
        }

        public NormativeBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTestSpec<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (factors == null) {
                throw new IllegalStateException("factors is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalStateException("inputs is required and must be non-empty");
            }
            if (Double.isNaN(threshold) || thresholdOrigin == null) {
                throw new IllegalStateException(
                        "threshold(value, origin) is required for a normative "
                                + "threshold-first probabilistic test");
            }
            return new ProbabilisticTestSpec<>(
                    useCaseFactory, factors, inputs, samples,
                    threshold, thresholdOrigin, contractRef, intent,
                    List.of(),
                    resources.build(),
                    latency,
                    Optional.ofNullable(assertOnOverride));
        }
    }

    // ── EmpiricalBuilder (sample-size-first / confidence-first) ────

    public static final class EmpiricalBuilder<FT, IT, OT> {

        private final Supplier<MeasureSpec<FT, IT, OT>> baseline;
        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private FT factors;
        private List<IT> inputs;
        private Integer samples;
        private double confidence = 0.95;
        private Double minDetectableEffect;
        private Double power;
        private String contractRef;
        private TestIntent intent = TestIntent.VERIFICATION;
        private final ResourceControlsBuilder resources = new ResourceControlsBuilder();
        private LatencySpec latency = LatencySpec.disabled();
        private VerdictDimension assertOnOverride;

        private EmpiricalBuilder(Supplier<MeasureSpec<FT, IT, OT>> baseline) {
            this.baseline = Objects.requireNonNull(baseline, "baseline");
        }

        public EmpiricalBuilder<FT, IT, OT> timeBudget(Duration budget) {
            resources.timeBudget(budget);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> tokenBudget(long tokens) {
            resources.tokenBudget(tokens);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> tokenCharge(long tokens) {
            resources.tokenCharge(tokens);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            resources.onBudgetExhausted(policy);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> onException(ExceptionPolicy policy) {
            resources.onException(policy);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> maxExampleFailures(int cap) {
            resources.maxExampleFailures(cap);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> latency(LatencySpec spec) {
            this.latency = Objects.requireNonNull(spec, "latency");
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> assertOn(VerdictDimension dimension) {
            this.assertOnOverride = Objects.requireNonNull(dimension, "dimension");
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> useCaseFactory(
                Function<FT, UseCase<FT, IT, OT>> factory) {
            this.useCaseFactory = Objects.requireNonNull(factory, "useCaseFactory");
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> factors(FT factors) {
            this.factors = Objects.requireNonNull(factors, "factors");
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final EmpiricalBuilder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(inputs));
        }

        public EmpiricalBuilder<FT, IT, OT> samples(int samples) {
            if (samples < 1) throw new IllegalArgumentException("samples must be ≥ 1");
            this.samples = samples;
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> confidence(double confidence) {
            if (confidence <= 0 || confidence >= 1) {
                throw new IllegalArgumentException("confidence must be in (0, 1)");
            }
            this.confidence = confidence;
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> minDetectableEffect(double mde) {
            if (mde <= 0 || mde >= 1) {
                throw new IllegalArgumentException("minDetectableEffect must be in (0, 1)");
            }
            this.minDetectableEffect = mde;
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> power(double power) {
            if (power <= 0 || power >= 1) {
                throw new IllegalArgumentException("power must be in (0, 1)");
            }
            this.power = power;
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> contractRef(String ref) {
            this.contractRef = ref;
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTestSpec<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (factors == null) {
                throw new IllegalStateException("factors is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalStateException("inputs is required and must be non-empty");
            }

            boolean sampleSizeFirst = samples != null;
            boolean confidenceFirst = minDetectableEffect != null || power != null;

            if (sampleSizeFirst && confidenceFirst) {
                throw new IllegalStateException(
                        "sample count is either declared or computed from power, "
                                + "not both; drop one of .samples() or "
                                + ".minDetectableEffect()/.power()");
            }
            if (confidenceFirst && (minDetectableEffect == null || power == null)) {
                throw new IllegalStateException(
                        "confidence-first requires both minDetectableEffect and power");
            }
            if (!sampleSizeFirst && !confidenceFirst) {
                throw new IllegalStateException(
                        "either .samples(n) or .minDetectableEffect(...) + .power(...) is required");
            }

            // Placeholder resolution: a later stage uses the baseline to
            // derive a real threshold (sample-size-first) or to compute
            // sample size from a power analysis (confidence-first). For
            // now we touch the supplier to make sure it resolves; the
            // real wiring lands alongside the statistics work.
            try {
                baseline.get();
            } catch (RuntimeException ignored) {
                // the baseline may not resolve yet; handled later.
            }

            int effectiveSamples = sampleSizeFirst ? samples : 100;  // placeholder
            double effectiveThreshold = 0.9;                         // placeholder

            List<String> defaultWarnings = List.of(
                    "threshold derivation is a placeholder — using 0.9",
                    sampleSizeFirst
                            ? "sample-size-first: verdict comparisons use placeholder stats"
                            : "confidence-first: sample size is a placeholder (n=100) pending a real power analysis");

            return new ProbabilisticTestSpec<>(
                    useCaseFactory, factors, inputs, effectiveSamples,
                    effectiveThreshold, ThresholdOrigin.EMPIRICAL, contractRef, intent,
                    defaultWarnings,
                    resources.build(),
                    latency,
                    Optional.ofNullable(assertOnOverride));
        }
    }
}
