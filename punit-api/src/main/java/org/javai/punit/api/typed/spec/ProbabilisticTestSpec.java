package org.javai.punit.api.typed.spec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test configured via one of three approaches:
 *
 * <ul>
 *   <li><b>PT01 threshold-first</b> — {@link #normative()} — a normative
 *       threshold is declared alongside a sample size.</li>
 *   <li><b>PT02 sample-size-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#samples(int)} — sample count declared,
 *       threshold derived from the baseline at Stage 4.</li>
 *   <li><b>PT03 confidence-first</b> — {@link #basedOn(Supplier)} +
 *       {@link EmpiricalBuilder#minDetectableEffect(double)} /
 *       {@link EmpiricalBuilder#power(double)} — sample count
 *       computed from a power analysis at Stage 4.</li>
 * </ul>
 *
 * <p>Builder constraints enforced at compile time by the type split:
 * calling {@code .threshold(...)} requires {@link NormativeBuilder};
 * calling {@code .minDetectableEffect(...)} / {@code .power(...)}
 * requires {@link EmpiricalBuilder}. Combinations that mix the two
 * paths do not compile.
 *
 * <p>Stage 2 implements the spec surface and placeholder verdict
 * synthesis. The placeholder evaluates "observed pass rate ≥
 * threshold → PASS"; Stage 4 replaces this with Wilson-score-based
 * verdict evaluation and, for PT03, a real power analysis.
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
            List<String> defaultWarnings) {
        this.useCaseFactory = useCaseFactory;
        this.factors = factors;
        this.inputs = inputs;
        this.samples = samples;
        this.threshold = threshold;
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.intent = intent;
        this.defaultWarnings = defaultWarnings;
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

    @Override public Iterator<Configuration<FT, IT>> configurations() {
        return List.of(new Configuration<>(factors, inputs, samples)).iterator();
    }

    @Override public void consume(Configuration<FT, IT> config, SampleSummary<OT> summary) {
        this.summary = summary;
    }

    @Override public EngineOutcome conclude() {
        int successes = summary == null ? 0 : summary.successes();
        int failures = summary == null ? 0 : summary.failures();
        double observed = summary == null ? Double.NaN : summary.passRate();
        Verdict verdict;
        if (summary == null || summary.total() == 0) {
            verdict = Verdict.INCONCLUSIVE;
        } else if (observed >= threshold) {
            verdict = Verdict.PASS;
        } else {
            verdict = Verdict.FAIL;
        }
        List<String> warnings = new ArrayList<>(defaultWarnings);
        warnings.add("statistics pending Stage 4 — verdict uses placeholder threshold comparison");
        return new EngineOutcome.ProbabilisticTestVerdict(
                new ProbabilisticTestVerdictOutcome(
                        verdict,
                        FactorBundle.of(factors),
                        successes,
                        failures,
                        threshold,
                        thresholdOrigin,
                        warnings));
    }

    public double threshold() { return threshold; }
    public ThresholdOrigin thresholdOrigin() { return thresholdOrigin; }
    public String contractRef() { return contractRef; }
    public TestIntent intent() { return intent; }
    public int samples() { return samples; }

    // ── NormativeBuilder (PT01 threshold-first) ────────────────────

    public static final class NormativeBuilder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private FT factors;
        private List<IT> inputs;
        private int samples = 100;
        private double threshold = Double.NaN;
        private ThresholdOrigin thresholdOrigin;
        private String contractRef;
        private TestIntent intent = TestIntent.VERIFICATION;

        private NormativeBuilder() {}

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
                        "threshold(value, origin) is required for normative (PT01) tests");
            }
            return new ProbabilisticTestSpec<>(
                    useCaseFactory, factors, inputs, samples,
                    threshold, thresholdOrigin, contractRef, intent,
                    List.of());
        }
    }

    // ── EmpiricalBuilder (PT02 / PT03) ─────────────────────────────

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

        private EmpiricalBuilder(Supplier<MeasureSpec<FT, IT, OT>> baseline) {
            this.baseline = Objects.requireNonNull(baseline, "baseline");
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

            boolean pt02 = samples != null;
            boolean pt03 = minDetectableEffect != null || power != null;

            if (pt02 && pt03) {
                throw new IllegalStateException(
                        "sample count is either declared (PT02) or computed from "
                                + "power (PT03), not both; drop one of .samples() or "
                                + ".minDetectableEffect()/.power()");
            }
            if (pt03 && (minDetectableEffect == null || power == null)) {
                throw new IllegalStateException(
                        "PT03 confidence-first requires both minDetectableEffect and power");
            }
            if (!pt02 && !pt03) {
                throw new IllegalStateException(
                        "either .samples(n) (PT02) or .minDetectableEffect(...) + .power(...) (PT03) is required");
            }

            // Stage 2 placeholder: Stage 4 uses the baseline to derive a real
            // threshold (PT02) or compute sample size (PT03). For now we
            // touch the supplier to make sure it resolves — Stage 4 will
            // consume it properly.
            try {
                baseline.get();
            } catch (RuntimeException ignored) {
                // the baseline may not resolve yet; that is a Stage 4 concern.
            }

            int effectiveSamples = pt02 ? samples : 100;  // Stage 4 replaces with real power calc
            double effectiveThreshold = 0.9;              // Stage 4 replaces with derived threshold

            List<String> defaultWarnings = List.of(
                    "threshold derivation pending Stage 4 — using placeholder threshold 0.9",
                    pt02
                            ? "PT02 sample-size-first: verdict comparisons use placeholder stats"
                            : "PT03 confidence-first: sample size pending Stage 4 power analysis, "
                                    + "using placeholder n=100");

            return new ProbabilisticTestSpec<>(
                    useCaseFactory, factors, inputs, effectiveSamples,
                    effectiveThreshold, ThresholdOrigin.EMPIRICAL, contractRef, intent,
                    defaultWarnings);
        }
    }
}
