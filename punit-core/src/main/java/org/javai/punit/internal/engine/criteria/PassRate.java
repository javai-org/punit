package org.javai.punit.internal.engine.criteria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.CriterionResult;
import org.javai.punit.api.spec.EmpiricalChecks;
import org.javai.punit.api.spec.EvaluationContext;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.DerivedThreshold;
import org.javai.punit.statistics.ThresholdDeriver;

/**
 * A Bernoulli pass-rate criterion. Reads {@link PassRateStatistics}
 * from the resolved baseline when in an empirical mode.
 *
 * <p>Three factory forms:
 * <ul>
 *   <li>{@link #meeting(double, ThresholdOrigin)} — contractual; the
 *       threshold is declared explicitly with non-empirical
 *       origin.</li>
 *   <li>{@link #empirical()} — default closest-match baseline
 *       resolution; threshold derived at evaluate time from the
 *       baseline's observed pass rate.</li>
 *   <li>{@link #empiricalFrom(Supplier)} — pinned baseline; threshold
 *       derived from the explicitly supplied baseline.</li>
 * </ul>
 *
 * <p>The empirical paths derive a threshold by applying the one-sided
 * Wilson lower bound at the test sample size to the baseline rate
 * (with a perfect-baseline two-step refinement when {@code k = n};
 * statistical companion §3.4 / §4.3.2), and PASS iff the observed
 * pass rate respects that derived threshold (§5.1). The underlying
 * statistical machinery is {@link BinomialProportionEstimator}; this
 * criterion holds no statistical code of its own — that's the punit
 * design rule (statistics live only in
 * {@code org.javai.punit.statistics}). See {@code CLAUDE.md}
 * §"Statistics isolation rule".
 *
 * <p>The contractual path keeps a deterministic
 * {@code observed >= threshold} check: an SLA-style threshold is an
 * external commitment, not a statistical claim against a baseline.
 *
 * <p>Lives in {@code punit-core} rather than {@code api package} because
 * the empirical path needs {@code BinomialProportionEstimator} and
 * {@code api package} is contractually free of statistics-library
 * dependencies. The {@link Criterion} interface itself stays in
 * {@code api package}; only this implementation moved.
 */
public final class PassRate<OT> implements Criterion<OT, PassRateStatistics> {

    private static final String NAME = "bernoulli-pass-rate";
    private static final double DEFAULT_CONFIDENCE = 0.95;
    private static final ThresholdDeriver DERIVER = new ThresholdDeriver();

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED }

    private final Mode mode;
    private final double threshold;
    private final ThresholdOrigin origin;
    private final double confidence;
    private final Supplier<Experiment> baselineSupplier;

    private PassRate(
            Mode mode,
            double threshold,
            ThresholdOrigin origin,
            double confidence,
            Supplier<Experiment> baselineSupplier) {
        this.mode = mode;
        this.threshold = threshold;
        this.origin = origin;
        this.confidence = confidence;
        this.baselineSupplier = baselineSupplier;
    }

    public static <OT> PassRate<OT> meeting(double threshold, ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (threshold < 0.0 || threshold > 1.0 || Double.isNaN(threshold)) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, 1], got " + threshold);
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call PassRate.empirical() or .empiricalFrom(...) instead");
        }
        return new PassRate<>(
                Mode.CONTRACTUAL, threshold, origin, DEFAULT_CONFIDENCE, null);
    }

    public static <OT> PassRate<OT> empirical() {
        return new PassRate<>(
                Mode.EMPIRICAL_DEFAULT, Double.NaN, ThresholdOrigin.EMPIRICAL, DEFAULT_CONFIDENCE, null);
    }

    public static <OT> PassRate<OT> empiricalFrom(Supplier<Experiment> baseline) {
        Objects.requireNonNull(baseline, "baseline");
        return new PassRate<>(
                Mode.EMPIRICAL_PINNED, Double.NaN, ThresholdOrigin.EMPIRICAL, DEFAULT_CONFIDENCE, baseline);
    }

    /**
     * Returns a new criterion with the declared confidence — used for
     * the empirical path's Wilson-score lower-bound comparison.
     */
    public PassRate<OT> atConfidence(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
        return new PassRate<>(mode, threshold, origin, confidence, baselineSupplier);
    }

    /**
     * The baseline supplier when this criterion was built with
     * {@link #empiricalFrom(Supplier)}; empty otherwise. The
     * framework consults this at spec-conclude time to route a
     * pinned baseline into the evaluation context.
     */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    /**
     * The confidence level (1 − α) used by the empirical Wilson-score
     * comparison. Defaults to {@code 0.95}; override via
     * {@link #atConfidence(double)}. Surfaced for the framework's
     * pre-flight feasibility check (see {@code PUnit}'s wire-up of
     * {@link org.javai.punit.statistics.VerificationFeasibilityEvaluator})
     * — given a configured {@code samples} and a resolved baseline
     * rate, the check needs the criterion's confidence to decide
     * whether the configuration is verification-grade.
     */
    public double confidence() {
        return confidence;
    }

    /**
     * The contractual target rate when this criterion was built with
     * {@link #meeting(double, ThresholdOrigin)}; empty for empirical
     * criteria, whose target is resolved from the baseline at
     * evaluate time.
     *
     * <p>Surfaced for the framework's pre-flight feasibility check:
     * a contractual SLA / SLO / POLICY threshold is no less in need
     * of statistical underwriting than an empirical one — n=50 with
     * a 99.99% target at 95% confidence is infeasible regardless of
     * where the 99.99% came from.
     */
    public OptionalDouble contractualTarget() {
        return mode == Mode.CONTRACTUAL
                ? OptionalDouble.of(threshold)
                : OptionalDouble.empty();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<PassRateStatistics> statisticsType() {
        return PassRateStatistics.class;
    }

    @Override
    public boolean isEmpirical() {
        return mode != Mode.CONTRACTUAL;
    }

    @Override
    public Map<String, Object> empiricalDetail() {
        if (mode == Mode.CONTRACTUAL) {
            return Map.of();
        }
        return Map.of("confidence", confidence);
    }

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, PassRateStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        int total = summary.total();
        if (total == 0) {
            return inconclusive("zero samples taken", Map.of());
        }

        double resolvedThreshold;
        ThresholdOrigin resolvedOrigin;
        Integer baselineSampleCount = null;
        Double baselineRate = null;

        if (mode == Mode.CONTRACTUAL) {
            resolvedThreshold = threshold;
            resolvedOrigin = origin;
        } else {
            PassRateStatistics stats = ctx.baseline().orElse(null);
            if (stats == null) {
                return EmpiricalChecks.noBaseline(NAME, empiricalDetail());
            }
            Map<String, Object> empiricalDetail = Map.of("confidence", confidence);
            // Inputs-identity rule precedes the sample-size rule: an identity
            // mismatch is a more fundamental violation, so its diagnostic
            // wins when both would fire.
            String baselineIdentity = ctx.baselineInputsIdentity().orElseThrow(() ->
                    new IllegalStateException(
                            "baseline statistics were resolved but baselineInputsIdentity is empty — "
                                    + "the BaselineProvider is producing inconsistent state"));
            Optional<CriterionResult> identityViolation = EmpiricalChecks.inputsIdentityMatch(
                    NAME, ctx.testInputsIdentity(), baselineIdentity, empiricalDetail);
            if (identityViolation.isPresent()) {
                return identityViolation.get();
            }
            Optional<CriterionResult> sizeViolation = EmpiricalChecks.sampleSizeConstraint(
                    NAME, total, stats.sampleCount(), empiricalDetail);
            if (sizeViolation.isPresent()) {
                return sizeViolation.get();
            }
            // Empirical: derive the threshold via the sample-size-first
            // construction at the test sample size (statistical companion
            // §3.4 / §4.3.2). The decision rule (§5.1) compares the raw
            // observed pass rate against this derived threshold.
            int baselineSuccesses = (int) Math.round(
                    stats.observedPassRate() * stats.sampleCount());
            DerivedThreshold derived = DERIVER.deriveSampleSizeFirst(
                    stats.sampleCount(), baselineSuccesses, total, confidence);
            resolvedThreshold = derived.value();
            resolvedOrigin = ThresholdOrigin.EMPIRICAL;
            baselineSampleCount = stats.sampleCount();
            baselineRate = stats.observedPassRate();
        }

        double observed = (double) summary.successes() / (double) total;
        Verdict verdict = observed >= resolvedThreshold ? Verdict.PASS : Verdict.FAIL;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("observed", observed);
        detail.put("threshold", resolvedThreshold);
        detail.put("origin", resolvedOrigin.name());
        detail.put("successes", summary.successes());
        detail.put("failures", summary.failures());
        detail.put("total", total);
        if (mode != Mode.CONTRACTUAL) {
            detail.put("confidence", confidence);
            detail.put("baselineSampleCount", baselineSampleCount);
            detail.put("baselineRate", baselineRate);
        }

        String explanation = mode == Mode.CONTRACTUAL
                ? String.format(
                        "observed=%.4f, threshold=%.4f (origin=%s) over %d samples",
                        observed, resolvedThreshold, resolvedOrigin, total)
                : String.format(
                        "observed=%.4f vs threshold=%.4f (Wilson-%.0f%% lower of "
                                + "baseline rate %.4f at n_test=%d; origin=%s) over %d samples",
                        observed, resolvedThreshold, confidence * 100.0,
                        baselineRate, total, resolvedOrigin, total);

        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }
}
