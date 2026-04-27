package org.javai.punit.api.typed.spec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.javai.punit.api.ThresholdOrigin;

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
 * <p>Empirical variants accept an {@link #atConfidence(double)} modifier
 * that Stage 4's Wilson-score-aware comparison will consume. Stage
 * 3.5 ships a placeholder inequality — {@code observed >= threshold}
 * — so the criterion surface is stable ahead of the statistics wiring.
 */
public final class BernoulliPassRate<OT> implements Criterion<OT, PassRateStatistics> {

    private static final String NAME = "bernoulli-pass-rate";
    private static final double DEFAULT_CONFIDENCE = 0.95;

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
     * Returns a new criterion with the declared confidence. Stage 4's
     * Wilson-score-aware comparison consumes this; Stage 3.5 carries
     * it so the authoring surface is stable.
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
            // Sample-size rule. See EmpiricalChecks#sampleSizeConstraint.
            Optional<CriterionResult> violation = EmpiricalChecks.sampleSizeConstraint(
                    NAME, total, stats.sampleCount(), Map.of());
            if (violation.isPresent()) {
                return violation.get();
            }
            resolvedThreshold = stats.observedPassRate();
            resolvedOrigin = ThresholdOrigin.EMPIRICAL;
            baselineSampleCount = stats.sampleCount();
        }

        double observed = (double) summary.successes() / (double) total;
        // Placeholder comparison — Stage 4 replaces with a Wilson-score-aware check.
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
        }

        String explanation = String.format(
                "observed=%.4f, threshold=%.4f (origin=%s) over %d samples",
                observed, resolvedThreshold, resolvedOrigin, total);

        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }
}
