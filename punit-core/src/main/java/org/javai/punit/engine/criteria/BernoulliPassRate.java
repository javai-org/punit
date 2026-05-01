package org.javai.punit.engine.criteria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * <p>The empirical paths wrap the observed pass rate in a Wilson-score
 * one-sided lower bound at the criterion's
 * {@link #atConfidence(double) confidence} (default 0.95) and PASS
 * iff that bound respects the baseline-derived threshold. The
 * underlying statistical machinery is
 * {@link BinomialProportionEstimator}; this criterion holds no
 * statistical code of its own — that's the punit design rule
 * (statistics live only in {@code org.javai.punit.statistics}). See
 * {@code CLAUDE.md} §"Statistics isolation rule".
 *
 * <p>The contractual path keeps a deterministic
 * {@code observed >= threshold} check: an SLA-style threshold is an
 * external commitment, not a statistical claim against a baseline.
 *
 * <p>Lives in {@code punit-core} rather than {@code punit-api} because
 * the empirical path needs {@code BinomialProportionEstimator} and
 * {@code punit-api} is contractually free of statistics-library
 * dependencies. The {@link Criterion} interface itself stays in
 * {@code punit-api}; only this implementation moved.
 */
public final class BernoulliPassRate<OT> implements Criterion<OT, PassRateStatistics> {

    private static final String NAME = "bernoulli-pass-rate";
    private static final double DEFAULT_CONFIDENCE = 0.95;
    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED }

    private final Mode mode;
    private final double threshold;
    private final ThresholdOrigin origin;
    private final double confidence;
    private final Supplier<Experiment> baselineSupplier;

    private BernoulliPassRate(
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

    public static <OT> BernoulliPassRate<OT> meeting(double threshold, ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (threshold < 0.0 || threshold > 1.0 || Double.isNaN(threshold)) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, 1], got " + threshold);
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call BernoulliPassRate.empirical() or .empiricalFrom(...) instead");
        }
        return new BernoulliPassRate<>(
                Mode.CONTRACTUAL, threshold, origin, DEFAULT_CONFIDENCE, null);
    }

    public static <OT> BernoulliPassRate<OT> empirical() {
        return new BernoulliPassRate<>(
                Mode.EMPIRICAL_DEFAULT, Double.NaN, ThresholdOrigin.EMPIRICAL,
                DEFAULT_CONFIDENCE, null);
    }

    public static <OT> BernoulliPassRate<OT> empiricalFrom(
            Supplier<Experiment> baseline) {
        Objects.requireNonNull(baseline, "baseline");
        return new BernoulliPassRate<>(
                Mode.EMPIRICAL_PINNED, Double.NaN, ThresholdOrigin.EMPIRICAL,
                DEFAULT_CONFIDENCE, baseline);
    }

    /**
     * Returns a new criterion with the declared confidence — used for
     * the empirical path's Wilson-score lower-bound comparison.
     */
    public BernoulliPassRate<OT> atConfidence(double confidence) {
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1), got " + confidence);
        }
        return new BernoulliPassRate<>(
                mode, threshold, origin, confidence, baselineSupplier);
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

        if (mode == Mode.CONTRACTUAL) {
            resolvedThreshold = threshold;
            resolvedOrigin = origin;
        } else {
            PassRateStatistics stats = ctx.baseline().orElse(null);
            if (stats == null) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("confidence", confidence);
                detail.put("origin", ThresholdOrigin.EMPIRICAL.name());
                return inconclusive(
                        "no matching baseline was resolvable for empirical threshold; "
                                + "see DG02 §'Baseline relationship' for the resolution mechanism",
                        detail);
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
            resolvedThreshold = stats.observedPassRate();
            resolvedOrigin = ThresholdOrigin.EMPIRICAL;
            baselineSampleCount = stats.sampleCount();
        }

        double observed = (double) summary.successes() / (double) total;
        Verdict verdict;
        Double wilsonLower = null;
        if (mode == Mode.CONTRACTUAL) {
            verdict = observed >= resolvedThreshold ? Verdict.PASS : Verdict.FAIL;
        } else {
            // Empirical: wrap the observed value in a Wilson-score one-sided
            // lower bound at the criterion's confidence; PASS iff the bound
            // respects the baseline-derived threshold. SC02 in the
            // orchestrator catalog. Calculation lives in the dedicated
            // statistics package — see CLAUDE.md §"Statistics isolation rule".
            wilsonLower = ESTIMATOR.lowerBound(summary.successes(), total, confidence);
            verdict = wilsonLower >= resolvedThreshold ? Verdict.PASS : Verdict.FAIL;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("observed", observed);
        detail.put("threshold", resolvedThreshold);
        detail.put("origin", resolvedOrigin.name());
        detail.put("successes", summary.successes());
        detail.put("failures", summary.failures());
        detail.put("total", total);
        if (mode != Mode.CONTRACTUAL) {
            detail.put("confidence", confidence);
            detail.put("wilsonLowerBound", wilsonLower);
            detail.put("baselineSampleCount", baselineSampleCount);
        }

        String explanation = mode == Mode.CONTRACTUAL
                ? String.format(
                        "observed=%.4f, threshold=%.4f (origin=%s) over %d samples",
                        observed, resolvedThreshold, resolvedOrigin, total)
                : String.format(
                        "observed=%.4f (Wilson-%.0f%% lower=%.4f) vs threshold=%.4f "
                                + "(origin=%s) over %d samples",
                        observed, confidence * 100.0, wilsonLower, resolvedThreshold,
                        resolvedOrigin, total);

        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }
}
