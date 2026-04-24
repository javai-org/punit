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
import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test configured via one of three statistical
 * approaches:
 *
 * <ul>
 *   <li><b>Threshold-first</b> — {@link #normative()} — a normative
 *       threshold is declared alongside a sample size.</li>
 *   <li><b>Sample-size-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#samples(int)} — sample count declared,
 *       threshold derived from the baseline at verdict time.</li>
 *   <li><b>Confidence-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#minDetectableEffect(double)} /
 *       {@link EmpiricalBuilder#power(double)} — sample count
 *       computed from a power analysis.</li>
 * </ul>
 *
 * <p>Builder constraints enforced at compile time by the type split:
 * calling {@code .threshold(...)} requires {@link NormativeBuilder};
 * calling {@code .minDetectableEffect(...)} / {@code .power(...)}
 * requires {@link EmpiricalBuilder}. Combinations that mix the two
 * paths do not compile.
 *
 * <p>Verdict synthesis is currently a placeholder that evaluates
 * "observed pass rate ≥ threshold → PASS"; a later stage replaces
 * this with Wilson-score-based verdict evaluation and, for the
 * confidence-first path, a real power analysis.
 */
public final class ProbabilisticTestSpec<FT, IT, OT> implements Spec<FT, IT, OT> {

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
    private final Optional<Dimension> assertOnOverride;

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
            Optional<Dimension> assertOnOverride) {
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
        double observed = summary == null ? Double.NaN : summary.passRate();
        Verdict functionalVerdict;
        if (summary == null || summary.total() == 0) {
            functionalVerdict = Verdict.INCONCLUSIVE;
        } else if (observed >= threshold) {
            functionalVerdict = Verdict.PASS;
        } else {
            functionalVerdict = Verdict.FAIL;
        }
        List<String> warnings = new ArrayList<>(defaultWarnings);
        warnings.add("statistics pending Stage 4 — verdict uses placeholder threshold comparison");

        Optional<LatencyVerdict> latencyVerdictOpt = evaluateLatency();
        Verdict projected = projectVerdict(functionalVerdict, latencyVerdictOpt);

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

    private Optional<LatencyVerdict> evaluateLatency() {
        LatencySpec latSpec = resourceControls.latency();
        if (latSpec.isDisabled() || summary == null || summary.total() == 0) {
            return Optional.empty();
        }
        LatencyResult observed = summary.latencyResult();
        List<PercentileBreach> breaches = new ArrayList<>();
        latSpec.p50Millis().ifPresent(t -> checkBreach("p50", t, observed.p50(), breaches));
        latSpec.p90Millis().ifPresent(t -> checkBreach("p90", t, observed.p90(), breaches));
        latSpec.p95Millis().ifPresent(t -> checkBreach("p95", t, observed.p95(), breaches));
        latSpec.p99Millis().ifPresent(t -> checkBreach("p99", t, observed.p99(), breaches));
        Verdict v = breaches.isEmpty() ? Verdict.PASS : Verdict.FAIL;
        return Optional.of(new LatencyVerdict(v, breaches));
    }

    private static void checkBreach(String name, long thresholdMillis,
                                    Duration observed, List<PercentileBreach> out) {
        Duration threshold = Duration.ofMillis(thresholdMillis);
        if (observed.compareTo(threshold) > 0) {
            out.add(new PercentileBreach(name, threshold, observed));
        }
    }

    private Verdict projectVerdict(Verdict functional, Optional<LatencyVerdict> latency) {
        Dimension dim = assertOn();
        Verdict lat = latency.map(LatencyVerdict::verdict).orElse(Verdict.PASS);
        return switch (dim) {
            case FUNCTIONAL -> functional;
            case LATENCY -> lat;
            case BOTH -> combineBoth(functional, lat);
        };
    }

    private static Verdict combineBoth(Verdict functional, Verdict latency) {
        if (functional == Verdict.INCONCLUSIVE || latency == Verdict.INCONCLUSIVE) {
            return Verdict.INCONCLUSIVE;
        }
        if (functional == Verdict.FAIL || latency == Verdict.FAIL) {
            return Verdict.FAIL;
        }
        return Verdict.PASS;
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
    @Override public LatencySpec latency() { return resourceControls.latency(); }

    /**
     * Which verdict dimension projects through the probabilistic
     * test's single-valued outcome. Derived from the spec's latency
     * configuration when not explicitly set:
     *
     * <ul>
     *   <li>{@link Dimension#BOTH} when a non-disabled
     *       {@link LatencySpec} is declared;</li>
     *   <li>{@link Dimension#FUNCTIONAL} otherwise.</li>
     * </ul>
     */
    public Dimension assertOn() {
        if (assertOnOverride.isPresent()) {
            return assertOnOverride.get();
        }
        return resourceControls.latency().hasAnyThreshold()
                ? Dimension.BOTH
                : Dimension.FUNCTIONAL;
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
        private Dimension assertOnOverride;

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
            resources.latency(spec);
            return this;
        }

        public NormativeBuilder<FT, IT, OT> assertOn(Dimension dimension) {
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
        private Dimension assertOnOverride;

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
            resources.latency(spec);
            return this;
        }

        public EmpiricalBuilder<FT, IT, OT> assertOn(Dimension dimension) {
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
                    Optional.ofNullable(assertOnOverride));
        }
    }
}
